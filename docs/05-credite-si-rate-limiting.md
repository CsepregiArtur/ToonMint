# 05 – Credite și rate limiting

Sistemul de credite este mecanismul care garantează că fiecare generare este plătită și
că marja rămâne pozitivă. Rate limiting-ul protejează infrastructura de abuz.

## 1. Reguli de business

| Element | Valoare | Unde se configurează |
|---|---|---|
| Cost per generare | 1 credit | `app.jobs.credits-per-job` |
| Credite abonament săptămânal | 50 | `app.revenuecat.products[...].credits` |
| Credite abonament anual | 300 | idem |
| Credite pachet suplimentar | 20 | idem |
| Cereri pe minut per utilizator | 10 | `app.rate-limit.requests-per-minute` |
| TTL imagine în cache | 10 minute | `app.temp-store.ttl` |
| Generări gratuite per email | 1 | `guest_trials.email` (UNIQUE) |

## 2. Fluxul unui credit

```text
POST /api/jobs
   │
   ├─ 1. rate limit (token bucket) ────► 429 + Retry-After dacă e depășit
   ├─ 2. validare imagine (format, dimensiune)
   ├─ 3. SELECT ... FOR UPDATE pe users   ← serializează debitările
   ├─ 4. sold >= cost? ────────────────► NU: 402 INSUFFICIENT_CREDITS
   ├─ 5. INSERT job (PENDING)
   ├─ 6. UPDATE users SET credit_balance = credit_balance - cost
   ├─ 7. INSERT credit_transactions (CONSUMPTION, -1, reference_job_id)
   └─ 8. COMMIT ──► worker pornește după commit
                        │
                        ├─ succes  → job COMPLETED, imagine în cache
                        └─ eșec    → job FAILED + INSERT REFUND (+1), sold refăcut
```

Cheia atomicității este `UserRepository.findByIdForUpdate` (blocare pessimistică):
două cereri simultane pentru același utilizator sunt executate una după alta, deci
soldul nu poate deveni negativ.

Rambursarea este idempotentă: `CreditService.refund` verifică
`existsByReferenceJobIdAndType(jobId, REFUND)` înainte de a credita, deci un job nu
poate fi rambursat de două ori (nici la retry, nici la marcare manuală `FAILED`).

## 2b. Generarea gratuită

Prima imagine a unui vizitator nu consumă credite: jobul are `credits_consumed = 0` și
`trial = true`. Protecția nu vine din sold, ci din **constrângerea unică pe `guest_trials.email`**.

| Situație | Rezultat |
|---|---|
| adresă nouă | job creat, 1 imagine, fără cont |
| aceeași adresă, a doua cerere | `402 TRIAL_ALREADY_USED` |
| două cereri simultane, aceeași adresă | una reușește, cealaltă primește 402 |
| adresa are cont și abonament | nu mai contează trial-ul; consumă credite |

Din perspectiva profitului, trial-ul este un cost de achiziție: costul AI al unei imagini
(~0.034 USD) este acceptat o singură dată per adresă. Pentru a limita abuzul cu multe
adrese, cererea este limitată și per IP (`trial-ip:<ip>`) cu aceeași limită pe minut.

## 3. Rate limiting

### Algoritm

Token bucket cu capacitate `R` (cereri pe minut) și refill `R/60` tokenuri pe secundă.

- Redis: scriptul Lua din `RedisTokenBucketRateLimiter` execută citirea, refill-ul și
  consumul atomic, deci mai multe instanțe ale backendului împart aceeași limită.
- Memorie: `InMemoryTokenBucketRateLimiter` (Caffeine-like, sincronizat per cheie),
  folosit în dezvoltare și ca **fallback** dacă Redis cade.

`RateLimitService` este apelat înainte de debitare, atât la crearea unui job, cât și
la retry. Răspunsul `429` conține header-ul `Retry-After` și detaliile
`retryAfterSeconds` / `requestsPerMinute`.

### De ce două mecanisme

| Mecanism | Protejează împotriva | Nu protejează împotriva |
|---|---|---|
| Credite | consum nedorit (fiecare generare costă) | rafalelor de cereri rapide |
| Rate limit | rafalelor / scripturilor | epuizării soldului |

Împreună acoperă atât costul, cât și stabilitatea.

## 4. Profitabilitate

Cost estimat al unei imagini Gemini 3.1 Flash Image la 1K: **~0.034 USD**.

| Plan | Preț | Credite | Cost AI maxim | Marja brută |
|---|---|---|---|---|
| Săptămânal | 4.99 USD | 50 | 1.70 USD | 3.29 USD (66%) |
| Anual | 24.99 USD | 300 | 10.20 USD | 14.79 USD (59%) |
| Pachet 20 credite | 1.99 USD | 20 | 0.68 USD | 1.31 USD (66%) |

Marja reală este mai mare, pentru că nu toți clienții consumă toate creditele lunii, iar
costurile fixe (hosting, DB) sunt mici la volum. Rate limiting-ul previne cazul în care
un singur client consumă toată marja într-o oră.

> Costurile se schimbă; verifică prețurile Gemini înainte de a ajusta pachetele.
> Regula simplă: cost AI per generare × 3 ≤ preț per credit.

## 5. Vizibilitate și reconciliere

- Soldul curent: `users.credit_balance`.
- Sursa de adevăr: `SUM(credit_transactions.amount)` per utilizator.

Interogare de verificare (PostgreSQL):

```sql
SELECT u.id,
       u.email,
       u.credit_balance,
       COALESCE(SUM(t.amount), 0) AS ledger_balance
FROM users u
LEFT JOIN credit_transactions t ON t.user_id = u.id
GROUP BY u.id, u.email, u.credit_balance
HAVING u.credit_balance <> COALESCE(SUM(t.amount), 0);
```

Rezultatul trebuie să fie gol. Orice diferență indică o scriere în afara
`CreditService`.

## 6. Ajustări operaționale

| Scenariu | Acțiune |
|---|---|
| Vrei să oferi credite gratuite | inserare manuală sau extinderea `CreditService.grant` cu tipul `BONUS` |
| Clientul a plătit, dar nu are credite | verifică `GET /api/admin/revenuecat-events`; retrimite evenimentul din RevenueCat |
| Vrei să crești limita de cereri | `RATE_LIMIT_REQUESTS_PER_MINUTE` (necesită restart) |
| Vrei să oprești complet rate limiting-ul | `app.rate-limit.enabled=false` (doar în dezvoltare) |
| Redis indisponibil în producție | limiter-ul local preia automat; verifică alertele |

## 7. Limitări cunoscute

- Limita per minut nu este distribuită când backendul rulează pe mai multe instanțe
  **fără** Redis (fiecare instanță are propriul bucket local).
- Creditele acordate de un pachet nu sunt retrase automat la refund; evenimentul
  `REFUND` pentru un consumabil este marcat `IGNORED` și necesită decizie manuală.
- `credit_balance` și ledger-ul trebuie menținute sincron; orice cod nou care modifică
  soldul trebuie să treacă prin `CreditService`, sub lock.
