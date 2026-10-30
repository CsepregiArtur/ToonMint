# 01 – Arhitectură

## 1. Vedere de ansamblu

```text
┌────────────────────┐        ┌──────────────────────────────────────────┐
│ Vue 3 + Pinia      │  HTTP  │ Spring Boot 3.5 (Java 25)                │
│ (frontend/)        │ ─────► │  /api/**                                 │
└────────────────────┘  JWT   │                                          │
                              │  Controller → Service → Repository       │
┌────────────────────┐        │        │         │                       │
│ Client KMP (viitor)│ ─────► │        │         ├── PostgreSQL (Flyway)  │
└────────────────────┘        │        │         ├── Redis (opțional)     │
                              │        │         └── cache imagini (TTL)   │
                              │        │                                │
                              │        ├── Gemini API (generare)         │
                              │        ├── RevenueCat (webhook)          │
                              │        └── Gmail SMTP (email)            │
                              └──────────────────────────────────────────┘
```

## 2. Module

| Pachet | Rol |
|---|---|
| `ai` | Interfața `ImageGenerationProvider`, implementările `GeminiImageProvider` și `StubImageProvider`, retry cu jitter, validare magic bytes, redimensionare |
| `config` | `AppProperties` (toată configurația), securitate, CORS, executor de job-uri, OpenAPI, bootstrap admin |
| `domain` | Entități JPA: `User`, `Job`, `CreditTransaction`, `SubscriptionEvent`, `EmailMessage` |
| `repository` | Spring Data JPA; două query-uri custom (`search`, `statsByStyle`) și blocarea pessimistică a utilizatorului |
| `security` | Emitere/validare JWT, filtru Bearer, contextul utilizatorului curent |
| `service` | Reguli de business: job-uri, credite, plăți RevenueCat, email, utilizatori |
| `storage` | Cache temporar de imagini (Caffeine sau Redis, TTL) |
| `ratelimit` | Token bucket: script Lua atomic în Redis + fallback în memorie |
| `web` | Controllere REST, DTO-uri, tratarea centralizată a erorilor |

## 3. Fluxul de generare

```text
Client                API                    DB                  Worker              Gemini
  │  POST /api/jobs    │                      │                    │                   │
  ├───────────────────►│ rate limit (token bucket)                 │                   │
  │                    ├── lock user ─────────►│                    │                   │
  │                    ├── verifică soldul     │                    │                   │
  │                    ├── INSERT job + CONSUMPTION ────────────────►│                   │
  │                    ├── cache imagine sursă (TTL 10 min)          │                   │
  │  202 { job }       │                      │                    │                   │
  │◄───────────────────┤  (după COMMIT) ──────┼───────────────────►│                   │
  │                    │                      │  UPDATE PROCESSING │                   │
  │  GET /api/jobs/{id}│                      │                    ├── request ───────►│
  │◄───────────────────┤                      │                    │◄── imagine ───────┤
  │                    │                      │  UPDATE COMPLETED  │                   │
  │                    │                      │  cache rezultat    │                   │
  │  GET .../download  │                      │  (TTL 10 min)      │                   │
  │◄───────────────────┤◄── bytes din cache ──┼────────────────────┘                   │
```

Puncte importante:

1. **Atomicitatea creditelor** — `SELECT ... FOR UPDATE` pe rândul utilizatorului
   serializează debitările; consumul și jobul sunt scrise în aceeași tranzacție.
2. **Procesarea pornește după commit** — `@TransactionalEventListener(AFTER_COMMIT)`
   trimite jobul pe un thread worker. Worker-ul deschide tranzacții JPA curate; dacă ar
   rula în callback-ul de commit pe același thread, EntityManager-ul tranzacției
   încheiate ar fi reutilizat și scrierile s-ar pierde.
3. **Nicio imagine pe disc** — sursa și rezultatul stau doar în `TempImageStore`
   (memorie sau Redis) cu TTL de 10 minute.
4. **Eșec = rambursare** — dacă providerul AI eșuează definitiv, jobul devine `FAILED`,
   se creează o tranzacție `REFUND` (o singură dată, verificat prin
   `reference_job_id` + `type`), iar soldul revine.

## 4. Fluxul de plată (RevenueCat)

```text
Client ── GET /api/billing/catalog ──► link pay.rev.cat?app_user_id=...
   │
   ├── checkout găzduit de RevenueCat ──► card / store
   │
RevenueCat ── POST /api/webhooks/revenuecat (Authorization: token) ──► Backend
   │                                                                     │
   │                                            event_id unic (idempotență)
   │                                                                     ├── SUBSCRIPTION_GRANT (50/300)
   │                                                                     ├── PURCHASE (20 credite)
   │                                                                     └── EXPIRATION → FREE
```

`app_user_id` este `users.public_id` (UUID), generat de backend. Astfel, același
identificator funcționează atât pentru Web Billing, cât și pentru SDK-urile mobile.

## 5. Schema bazei de date

```text
users (1) ────< jobs (N)
users (1) ────< credit_transactions (N)
jobs  (1) ────< credit_transactions (N, opțional)
users (1) ────< subscription_events (N)
users (1) ────< email_messages (N)
```

| Tabelă | Scop | Câmpuri cheie |
|---|---|---|
| `users` | conturi, sold curent, abonament | `public_id`, `email` unic, `credit_balance`, `subscription_type`, `subscription_expires_at`, `role` |
| `jobs` | metadate job-uri (fără imagini) | `user_id`, `style_id`, `status`, `credits_consumed`, `attempts`, `created_at` |
| `credit_transactions` | ledger-ul de credite (sursa de adevăr) | `amount`, `type`, `reference_job_id`, `external_reference` unic |
| `subscription_events` | jurnal webhook-uri RevenueCat | `event_id` unic, `status`, `payload` |
| `email_messages` | audit pentru emailuri | `sent_by_user_id`, `recipient_count`, `status` |

Migrarea este în `backend/src/main/resources/db/migration/V1__init.sql` și rulează
identic pe PostgreSQL și H2 (mod `PostgreSQL`).

## 6. Starea runtime

| Coș | `dev` (implicit) | `prod` |
|---|---|---|
| Bază de date | H2 pe disc (`backend/data/`) | PostgreSQL 16 |
| Redis | nu (rate limit + cache în memorie) | opțional — recomandat la mai multe instanțe |
| AI | `stub` (filtru local) | Gemini `gemini-3.1-flash-image` |
| Email | simulat (log) | Gmail SMTP |
| RevenueCat | link/webhook opțional | obligatoriu pentru plăți |

## 7. Decizii și compromisuri

- **Ledger + sold denormalizat** — `credit_transactions` este istoricul complet, iar
  `users.credit_balance` este soldul curent, actualizat sub lock. Astfel citirea
  soldului este ieftină, iar orice diferență poate fi reconstruită din ledger.
- **Ștergere logică** — joburile șterse devin `REMOVED`, păstrând trasabilitatea
  tranzacțiilor.
- **Cache de imagini** — Redis în producție (partajat între instanțe) și Caffeine în
  dezvoltare; în ambele cazuri datele expiră fără intervenție.
- **Rate limiting fail-open pe Redis** — dacă Redis cade, limiter-ul local preia rolul,
  ca aplicația să rămână disponibilă; creditele rămân protejate de lock-ul din baza de date.
