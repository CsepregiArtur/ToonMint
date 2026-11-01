# 06 – Dezvoltare și testare

## 1. Cerințe

| Unealtă | Versiune |
|---|---|
| JDK | 25+ (proiectul este compilat cu `release 25`) |
| Maven | 3.9+ |
| Node.js | 20+ |
| PostgreSQL | doar pentru profilul `prod` |
| Redis | opțional (recomandat doar la mai multe instanțe) |

## 2. Structura comenzilor

```bash
# backend
cd backend
mvn -q -DskipTests compile          # compilare rapidă
mvn spring-boot:run                 # profil dev (H2, AI stub, email simulat)
mvn test                            # suita de teste
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# frontend
cd frontend
npm install
npm run dev                         # http://localhost:5173 (proxy /api → 8080)
npm run build                       # build de producție în dist/
```

### 2.1 Aplicațiile mobile (Capacitor)

Shell-urile native refolosesc exact build-ul din `dist/`. Logica aplicației nu se
scrie în `ios/` sau `android/` — doar `npm run build` + sincronizare. Singurele
excepții, o singură dată, sunt permisiunile: `Info.plist` (iOS) și
`AndroidManifest.xml` (Android).

```bash
cd frontend
npm run build
npx cap sync ios                    # copiază dist/ + actualizează Package.swift
npx cap open ios                    # deschide Xcode (sau build direct, mai jos)

# build pentru simulator, fără semnare:
xcodebuild -project ios/App/App.xcodeproj -scheme App -sdk iphonesimulator \
  -configuration Debug -destination 'generic/platform=iOS Simulator' \
  CODE_SIGNING_ALLOWED=NO build

# Android: Gradle nu acceptă Java 27 de pe sistem, deci JAVA_HOME se fixează:
npx cap sync android
cd android
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
```

Pentru ca aplicația să ajungă la server, build-ul mobil are nevoie de
`VITE_API_BASE_URL=https://toonmint.cs-hub.xyz/api` în `frontend/.env` — altfel
`/api` ar fi relativ la `capacitor://localhost`. Linia rămâne comentată pentru
build-ul web, unde `/api` trebuie să rămână relativ. Cumpărările cer în plus
`VITE_REVENUECAT_IOS_API_KEY` / `VITE_REVENUECAT_ANDROID_API_KEY`.

`ios/App/App/public/` și `android/app/src/main/assets/public/` sunt generate (ignorate
de git), deci după clonare se rulează întâi `npm run build` și apoi `npx cap sync`.
Detalii: [13 – Aplicații mobile](13-mobil-ios-android.md).

### 2.2 Build nativ (GraalVM)

Profilul `native` din `backend/pom.xml` produce un executabil self-contained în loc
de `java -jar`:

```bash
cd backend
mvn -Pnative native:compile -DskipTests    # → target/toonmint-backend
```

Build-ul are nevoie de GraalVM (NU de un JDK obișnuit) și de ~8 GB RAM, 10–20 de
minute CPU. Nu se rulează pe mașina de dezvoltare sau pe serverul de producție:
`native-image` **nu cross-compilează**, deci un binar pentru Linux se poate face
doar pe Linux. De aceea build-ul rulează în CI
(`.github/workflows/native.yml`, `ubuntu-latest`), iar artefactul `toonmint-native`
se descarcă de acolo.

Workflow-ul verifică binarul înainte de a-l publica: îl pornește pe profilul `dev`
(H2 local + `StubImageProvider`, fără servicii externe), așteaptă
`/actuator/health`, apoi parcurge un job complet (login demo → `POST /api/jobs` →
`COMPLETED` → `download`). Pasul din urmă este important: `ImageIO` și Thumbnailator
(`ImageOptimizer`, `StylePreviewService`) sunt cea mai fragilă parte în native-image,
iar un simplu `health=UP` nu atinge deloc AWT.

## 3. Servicii externe fără Docker

Proiectul rulează direct pe sistem, fără containere. Profilul `dev` nu are nevoie de
niciun serviciu extern; pentru `prod` instalezi PostgreSQL nativ, iar Redis doar dacă
rulezi mai multe instanțe.

### 3.1 PostgreSQL

**macOS (Homebrew)**

```bash
brew install postgresql@16
brew services start postgresql@16        # pornește automat la login
echo 'export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"' >> ~/.zshrc

createuser -s toonmint                    # superuser local pentru dezvoltare
createdb -O toonmint toonmint
psql -d toonmint -c "ALTER USER toonmint PASSWORD 'toonmint';"
psql -d toonmint -c "SELECT version();"   # verificare
```

**Linux (Debian/Ubuntu)**

```bash
sudo apt update && sudo apt install postgresql
sudo -u postgres psql -c "CREATE USER toonmint WITH PASSWORD 'toonmint';"
sudo -u postgres createdb -O toonmint toonmint
sudo systemctl enable --now postgresql
```

**Linux (RHEL/Fedora)**

```bash
sudo dnf install postgresql-server postgresql
sudo postgresql-setup --initdb
sudo systemctl enable --now postgresql
sudo -u postgres createuser -P toonmint && sudo -u postgres createdb -O toonmint toonmint
```

Migrațiile Flyway creează toate tabelele la prima pornire — nu trebuie să rulezi SQL
manual.

### 3.2 Redis (opțional)

```bash
# macOS
brew install redis && brew services start redis

# Debian/Ubuntu
sudo apt install redis-server && sudo systemctl enable --now redis-server

# verificare
redis-cli ping        # → PONG
```

Fără Redis, în `.env`:

```bash
RATE_LIMIT_BACKEND=memory
TEMP_STORE_BACKEND=memory
REDIS_HEALTH_ENABLED=false
```

Consecințe: limita de cereri pe minut și cache-ul de imagini devin locale procesului.
Este suficient pentru o singură instanță; cu două sau mai multe instanțe ai nevoie de
Redis ca limitele și cache-ul să fie partajate.

### 3.3 Oprire și curățare

```bash
brew services stop postgresql@16 redis            # macOS
sudo systemctl stop postgresql redis-server       # Linux

dropdb toonmint && createdb -O toonmint toonmint # reset complet al bazei
```

## 4. Profiluri

### Variabile de mediu

Spring Boot încarcă automat `.env` din rădăcina repo-ului sau din `backend/`
(`spring.config.import: optional:file:./.env[.properties]`). Nu este nevoie de
`export` în shell; fișierul este în `.gitignore`.

```bash
cd /Users/.../ToonMint
cp .env.example .env
# editează .env: GEMINI_API_KEY, MAIL_USERNAME, REVENUECAT_...
```

### `dev` (implicit)

| Aspect | Valoare |
|---|---|
| Bază de date | H2 pe disc: `backend/data/toonmint.mv.db` |
| Migrații | Flyway rulează aceleași scripturi ca pe PostgreSQL |
| AI | `auto`: Gemini dacă există `GEMINI_API_KEY`, altfel `StubImageProvider` |
| Rate limit | în memorie |
| Cache imagini | Caffeine, TTL 10 min |
| Email | simulat (`simulated: true`) |
| Conturi demo | active: `demo@toonmint.local` / `demo12345` și `operator@toonmint.local` / `operator12345` |
| Generare gratuită | 1 imagine per email, fără cont |

### `prod`

PostgreSQL + Gemini + Gmail SMTP, cu Redis opțional. Cere `JWT_SECRET` și
`REVENUECAT_WEBHOOK_AUTH_TOKEN` pentru a porni cu plăți funcționale.

| Aspect | Valoare implicită | Fără Redis |
|---|---|---|
| Rate limit | `redis` | `RATE_LIMIT_BACKEND=memory` |
| Cache imagini | `redis` | `TEMP_STORE_BACKEND=memory` |
| Health check Redis | activ | `REDIS_HEALTH_ENABLED=false` |

### `test`

Folosit automat de suita de teste: H2 în memorie, AI `stub`, email dezactivat,
limitare de 1000 cereri/minut, `app.jobs.threads=1`.

## 5. Teste

Suita are 56 de teste de integrare (`@SpringBootTest` + `MockMvc`) care acoperă
fluxurile complete:

| Clasă | Acoperă |
|---|---|
| `AuthAndUserApiTest` | register → cod → verify → login, login blocat înainte de confirmare, email duplicat, acces anonim, izolare între utilizatori, actualizare profil |
| `EmailVerificationApiTest` | cod greșit, limită de 5 încercări, cooldown la retrimitere, fără enumerare de adrese, resetare parolă și cod single-use |
| `JobApiTest` | ciclu complet multipart (upload → COMPLETED → download → ștergere), upload base64, stil invalid, fișier non-imagine |
| `CreditsApiTest` | `402` fără credite, catalog RevenueCat, link de cumpărare, respingerea produselor de tip abonament |
| `RevenueCatWebhookTest` | abonament, pachet de credite, expirare, refund, token invalid, produs necunoscut, **idempotență** |
| `JobFailureRefundTest` | rambursare automată la eșec + lipsa dublei rambursări |
| `AdminApiTest` | email simulat, acces interzis utilizatorilor, statistici, liste de admin |
| `RateLimitTest` | a doua cerere în aceeași fereastră → `429` + `Retry-After` |
| `GuestTrialApiTest` | generare anonimă, blocarea celei de-a doua cereri, eligibilitate, email invalid, job inexistent |
| `DemoLoginApiTest` | disponibilitate, token utilizabil, rol `ADMIN` |
| `StyleCatalogTest` | cel puțin 20 de stiluri, etichete unice, prompturi ne-goale, `previewUrl` pentru fiecare, endpoint public |
| `FinanceApiTest` | cost AI per generare finalizată, încasare din webhook (cu fallback pe eticheta de preț), comision + refund, idempotență la replay, cheltuieli manuale, acces doar pentru operator |

Rulare țintită:

```bash
mvn test -Dtest=JobApiTest
mvn test -Dtest='RevenueCatWebhookTest#initialPurchaseActivatesSubscriptionAndGrantsCredits'
```

## 6. Convenții de cod (necesare pentru depanare)

1. **Fiecare metodă publică are un Javadoc de o frază**, în română, care spune *ce
   face* metoda — nu *cum*. La depanare, un `grep` după comportament găsește imediat
   punctul de intrare.
2. Comentariile explică **de ce**, nu **ce**: decizii, capcane, motive pentru lock-uri
   sau retry-uri.
3. Numele sunt în engleză (API, DB, cod), documentația utilizatorului este în română.
4. Secretele vin exclusiv din variabile de mediu; `.env` nu se comite.
5. Fiecare regulă de business nouă (costuri, limite) primește un test de integrare.

## 7. Verificări înainte de commit

```bash
cd backend  && mvn -q clean test
cd frontend && npm run build
```

Dacă `mvn test` semnalează `class path resource [...] cannot be opened`, rulează
`mvn clean test` — este un artefact de clasă ștearsă incremental.

## 8. Depanare frecventă

| Simptom | Cauză probabilă | Verificare |
|---|---|---|
| jobul rămâne `PENDING` | worker-ul nu a pornit | log `Job N completed/failed`; verifică `app.jobs.threads > 0` |
| `402 INSUFFICIENT_CREDITS` neașteptat | sold 0 după un consum anterior | `GET /api/users/me`, apoi `GET /api/credits/transactions` |
| `410 IMAGE_EXPIRED` la download | au trecut peste 10 minute de la generare | setează `app.temp-store.ttl` mai mare sau regenerează |
| `429 RATE_LIMIT_EXCEEDED` | peste 10 cereri/minut | vezi `Retry-After`; crește `RATE_LIMIT_REQUESTS_PER_MINUTE` |
| creditele nu apar după plată | webhook nu ajunge / token greșit | `GET /api/admin/revenuecat-events` |
| `503 PAYMENTS_NOT_CONFIGURED` | lipsește linkul RevenueCat | setează `REVENUECAT_PURCHASE_LINK_URL` |
| emailurile nu pleacă | `MAIL_ENABLED=false` sau parolă de cont | vezi [04 – Email](04-email-google-smtp.md) |
| `CORS` în browser | origine nepermisă | `CORS_ALLOWED_ORIGINS=http://localhost:5173` |

## 9. Resetarea mediului local

```bash
rm -rf backend/data                     # șterge baza H2 (profil dev)
mvn -f backend/pom.xml clean            # șterge artefactele
rm -rf frontend/dist                    # șterge build-ul frontend

# doar dacă vrei să reiei instalarea dependențelor
rm -rf frontend/node_modules

# reset complet al bazei PostgreSQL (profil prod)
dropdb toonmint && createdb -O toonmint toonmint
```
