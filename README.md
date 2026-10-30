# ToonMint

Aplicație photo-to-cartoon: utilizatorul încarcă o fotografie, alege un stil, iar serverul
generează o versiune cartoon/anime. Imaginile **nu sunt stocate permanent** — sunt ținute
doar într-un cache temporar cu TTL, iar în baza de date rămân doar metadatele.

| Strat | Tehnologie |
|---|---|
| Backend | Java 25 + Spring Boot 3.5 (Web, Security, Data JPA, Validation, Mail, Actuator) |
| Bază de date | PostgreSQL 16 + Flyway (H2 în dezvoltare locală) |
| Cache / rate limit | Redis (token bucket Lua), fallback în memorie |
| Generare imagini | Google Gemini 3.1 Flash Image (sau provider `stub` offline) |
| Plăți | RevenueCat (abonamente + pachete de credite, prin webhook) |
| Email | Gmail SMTP (Spring Boot Mail) |
| Frontend | Vue 3 + Pinia + Vue Router + Vite + Axios |
| Documentație API | Swagger / OpenAPI (`/swagger-ui.html`) |

## Pornire rapidă (fără servicii externe)

Profilul implicit `dev` folosește H2 pe disc, provider AI `stub`, rate limiting în memorie
și email dezactivat — nu ai nevoie de PostgreSQL, Redis sau chei API.

```bash
# backend
cd backend
mvn spring-boot:run
# → http://localhost:8080/swagger-ui.html

# frontend (alt terminal)
cd frontend
npm install
npm run dev
# → http://localhost:5173
```

Cont de operator local (opțional):

```bash
ADMIN_EMAIL=admin@toonmint.local ADMIN_PASSWORD=parola-ta-mvn spring-boot:run
```

## Pornire cu PostgreSQL (profil `prod`)

Proiectul nu folosește Docker. Serviciile se instalează nativ pe sistem.

### macOS (Homebrew)

```bash
brew install postgresql@16
brew services start postgresql@16
echo 'export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"' >> ~/.zshrc

# o singură dată: creează rolul și baza
createuser -s toonmint
createdb -O toonmint toonmint
psql -d toonmint -c "ALTER USER toonmint PASSWORD 'toonmint';"
```

### Linux (Debian/Ubuntu)

```bash
sudo apt install postgresql
sudo -u postgres psql -c "CREATE USER toonmint WITH PASSWORD 'toonmint';"
sudo -u postgres createdb -O toonmint toonmint
sudo systemctl enable --now postgresql
```

### Redis (opțional)

Necesar doar când rulezi mai multe instanțe ale backendului:

```bash
brew install redis && brew services start redis      # macOS
sudo apt install redis-server                        # Debian/Ubuntu
```

### Pornire

```bash
cp .env.example .env          # completează secretele
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

Profilul `prod` cere obligatoriu `JWT_SECRET` și folosește PostgreSQL, Gemini și Gmail
SMTP. **Redis este opțional** pentru o singură instanță — fără el, setează în `.env`:

```bash
RATE_LIMIT_BACKEND=memory
TEMP_STORE_BACKEND=memory
REDIS_HEALTH_ENABLED=false
```

Redis devine necesar doar când rulezi mai multe instanțe ale backendului (limită de
cereri și cache de imagini partajate). Detalii: [docs/06](docs/06-dezvoltare-si-testare.md).

## Rulare teste

```bash
cd backend && mvn test        # 24 de teste de integrare pe H2
cd frontend && npm run build  # verifică build-ul Vue
```

## Configurare externă

| Ce | Unde e documentat |
|---|---|
| Plăți RevenueCat (produse, webhook, linkuri de checkout) | [docs/03-plati-revenuecat.md](docs/03-plati-revenuecat.md) |
| Email prin Gmail SMTP (parolă de aplicație) | [docs/04-email-google-smtp.md](docs/04-email-google-smtp.md) |
| Credite, rate limiting, marjă de profit | [docs/05-credite-si-rate-limiting.md](docs/05-credite-si-rate-limiting.md) |
| Variabile de mediu | [.env.example](.env.example) |

## Documentație

- [00 – Specificație și conformitate](docs/00-specificatie-si-conformitate.md)
- [01 – Arhitectură](docs/01-arhitectura.md)
- [02 – API](docs/02-api.md)
- [03 – Plăți RevenueCat](docs/03-plati-revenuecat.md)
- [04 – Email Google SMTP](docs/04-email-google-smtp.md)
- [05 – Credite și rate limiting](docs/05-credite-si-rate-limiting.md)
- [06 – Dezvoltare și testare](docs/06-dezvoltare-si-testare.md)
- [07 – Securitate](docs/07-securitate.md)

## Structura proiectului

```text
backend/
  src/main/java/com/toonmint/
    ai/            generator de imagini (Gemini + stub offline)
    config/        proprietăți, securitate, executori, OpenAPI
    domain/        entități JPA (users, jobs, credit_transactions, ...)
    ratelimit/     token bucket Redis/Lua + fallback local
    repository/    Spring Data repositories (inclusiv custom queries)
    security/      JWT, filtru de autentificare, context utilizator
    service/       logica de business (job-uri, credite, plăți, email)
    storage/       cache temporar pentru imagini
    web/           controllere REST + DTO-uri + tratarea erorilor
  src/main/resources/db/migration/V1__init.sql
frontend/
  src/components/  JobUploader, JobList, CreditStore, AdminPanel
  src/stores/      Pinia: auth, jobs, credits, admin
  src/views/       Login, Studio, Credits, Admin
docs/              documentație în română
```

## Fluxuri principale

1. **Generare**: upload → validare → rate limit → debitare credit (lock pessimistic) →
   job `PENDING` → worker după commit → Gemini (retry exponențial + jitter) → imagine în
   cache 10 min → `COMPLETED` sau `FAILED` + rambursare automată.
2. **Plată**: frontend cere link RevenueCat → checkout găzduit de RevenueCat → webhook
   semnat → activare abonament / acordare credite, cu idempotență pe `event_id`.
3. **Email**: operatorul selectează utilizatori → backend trimite prin Gmail SMTP și
   înregistrează rezultatul în `email_messages`.
