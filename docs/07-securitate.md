# 07 – Securitate

## 1. Autentificare și autorizare

| Aspect | Implementare |
|---|---|
| Autentificare | JWT HS256, semnat cu `app.jwt.secret` (minim 32 bytes, verificat la start) |
| Durată token | 24h (`app.jwt.ttl`) |
| Token de refresh | `app.jwt.refresh-ttl` (90 zile), aleator de 256 biți, stocat doar ca SHA-256 în `refresh_tokens` |
| Rotația tokenului | fiecare refresh consumă tokenul și emite altul; refolosirea unui token consumat închide toate sesiunile contului |
| Parole | BCrypt (`PasswordEncoder`) |
| Sesiuni | stateless — filtrul JWT populează `SecurityContext` la fiecare cerere |
| Roluri | `USER`, `ADMIN`; `@EnableMethodSecurity` disponibil pentru reguli viitoare |
| Verificare email | cod OTP de 6 cifre, obligatoriu înainte de primul login |
| Resetare parolă | cod OTP de 6 cifre, single-use |

Codurile OTP (`verification_codes`):

- generate cu `SecureRandom` și stocate **doar** ca hash BCrypt — un dump al bazei nu
  dezvăluie coduri;
- expiră după 15 minute, acceptă maximum 5 încercări, apoi sunt invalidate;
- un cod nou poate fi cerut după 60 de secunde; cererile sunt limitate și pe email, și pe IP;
- răspunsurile pentru `resend-verification` și `forgot-password` sunt identice pentru
  adrese existente și inexistente (fără enumerare de utilizatori);
- confirmarea unui cod de resetare marchează adresa ca verificată (dovada accesului la email).

Reguli de acces (`SecurityConfig`):

| Rută | Acces |
|---|---|
| `/api/auth/**`, `POST /api/users` | public |
| `POST /api/webhooks/revenuecat` | public, dar validat prin token dedicat |
| `/api/public/**` | public (trial limitat per email și IP) |
| `/api/admin/**` | `ROLE_ADMIN` |
| `/swagger-ui/**`, `/v3/api-docs/**`, `/actuator/health` | public |
| restul | autentificat |

Autorizarea la nivel de obiect este explicită în servicii:

- un utilizator vede doar propriile job-uri, tranzacții și profil;
- operatorul vede tot, dar nu poate citi parole (nu sunt expuse în DTO-uri);
- `PUT /api/jobs/{id}/status`, `GET /api/jobs`, `GET /api/jobs/stats` cer rol `ADMIN`.

## 2. Protecția plăților

- Webhook-ul RevenueCat cere header `Authorization` identic cu
  `REVENUECAT_WEBHOOK_AUTH_TOKEN`, comparat cu `MessageDigest.isEqual` (timp constant,
  fără scurgeri prin timing).
- Dacă tokenul nu este configurat, răspunsul este `503` — **fail closed**, nu se
  acceptă evenimente nesemnate.
- Idempotență dublă: `subscription_events.event_id` unic + `credit_transactions.
  external_reference` unic. Retrimiterile nu pot dubla creditele.
- Creditele nu sunt acordate niciodată din request-ul clientului: doar din webhook.

## 3. Protecția resurselor

| Risc | Măsură |
|---|---|
| Upload de fișiere arbitrare | validare după magic bytes (`ImageBytes`), nu după `Content-Type` |
| Fișiere uriașe | limită configurabilă (`app.jobs.max-input-bytes`, implicit 10 MB) + `413` |
| Bombă de decompresie | imaginea este decodată, redimensionată și re-encodată înainte de livrare |
| Supraîncărcare | rate limiting per utilizator + pool de thread-uri mărginit + coadă mărginită |
| Consum dublu de credite | `SELECT ... FOR UPDATE` pe rândul utilizatorului |
| Dublă rambursare | verificare `reference_job_id` + tip `REFUND` |
| Suprapunere de credite la webhook | cheie unică pe event id |

## 3b. Generarea gratuită și login-ul demo

- Trial-ul este limitat de `guest_trials.email` (UNIQUE) și de rate limiting per email
  și per IP. Nu este o barieră anti-fraudă completă: o persoană cu multe adrese poate
  obține mai multe imagini. Conturile propriu-zise folosesc verificare de email cu cod
  OTP; pentru trial (fără cont) se recomandă suplimentar un captcha.
- `publicId` (UUID v4) este singurul „token” al unui job de trial: nu este ghicibil, dar
  nici revocabil. Expiră practic odată cu cache-ul imaginii (10 minute).
- Joburile de trial nu au utilizator; ele apar în consola de operator cu `guestEmail`
  pentru eventuale investigații de abuz.
- `POST /api/auth/demo-login` emite token valid **fără parolă**. Răspunde doar când
  `app.demo.enabled=true`; cu valoarea `false` (profilul `prod`) întoarce `404
  DEMO_DISABLED` și seeder-ul nu creează conturi. Verificarea e la runtime, nu prin
  `@ConditionalOnProperty` — în binarul nativ acea condiție e evaluată de Spring AOT la
  build, deci nu ar respecta configurația de la pornire. Nu activa proprietatea pe un
  mediu public: conturile demo au parole fixe, cunoscute.

## 4. Date și confidențialitate

- **Nicio imagine nu atinge discul sau baza de date.** Sursa și rezultatul stau doar în
  cache-ul temporar (memorie/Redis), cu TTL de 10 minute.
- `DELETE /api/jobs/{id}` elimină imediat imaginea din cache.
- În baza de date rămân doar metadate: proprietar, stil, status, credite, momente.
- Expunerea este minimizată: toate răspunsurile folosesc DTO-uri, niciodată entități.
- Erorile neașteptate returnează un mesaj generic; detaliile rămân în log-ul serverului.

## 5. Secrete

| Secret | Sursă | Reguli |
|---|---|---|
| `JWT_SECRET` | variabilă de mediu | minim 32 bytes; rotește-l periodic (invalidează tokenurile) |
| `GEMINI_API_KEY` | variabilă de mediu | nu se loghează; cheia apare doar în URL-ul intern către Google |
| `MAIL_PASSWORD` | variabilă de mediu | parolă de aplicație Gmail, nu parola contului |
| `REVENUECAT_WEBHOOK_AUTH_TOKEN` | variabilă de mediu | lung, aleator, diferit de mediul de test |
| `ADMIN_PASSWORD` | variabilă de mediu | doar pentru bootstrap; schimbă parola după prima conectare |

`.gitignore` exclude `.env`, `.env.local`, `*.pem`. Nu există secrete în cod, în
`application.yml` sau în teste (testele folosesc valori evident fictive).

## 6. Configurare de producție

1. Rulează cu HTTPS în față (reverse proxy / load balancer) — tokenul JWT circulă în
   header.
2. Setează `CORS_ALLOWED_ORIGINS` la domeniile reale, nu `*`.
3. Dezactivează `springdoc` sau protejează-l dacă API-ul nu este public.
4. Restrânge `/actuator/**` la rețeaua internă (implicit sunt expuse doar
   `health` și `info`).
5. Folosește un utilizator de bază de date cu privilegii minime (fără `SUPERUSER`).
6. Activează backup-uri pentru PostgreSQL; Redis poate fi reconstruit (conține doar
   cache cu TTL).
7. Monitorizează `429` și `402` — creșteri bruște indică abuz sau probleme de facturare.

## 7. Limitări cunoscute și pași următori

- JWT-urile de acces nu au revocare individuală: logout-ul și resetarea parolei anulează
  tokenul de refresh, dar un JWT deja emis rămâne valid până la expirare (24h). Pentru
  revocare imediată ar fi nevoie de o versiune de token per utilizator, verificată în
  filtrul JWT — cost: o citire per cerere.
- Tokenul de refresh stă în `localStorage`, ca și tokenul de acces. Într-un XSS el poate
  fi citit; rotația și detectarea refolosirii limitează fereastra, dar mutarea în cookie
  `HttpOnly` (sau în Keychain/Keystore pe mobil) ar închide complet problema.
- `POST /api/auth/login` nu are limitare separată pe IP (există doar rate limiting pe
  cont la generare); recomandat un bucket pe IP pentru protecție la brute-force.
- Codurile OTP sunt protejate prin expirare (15 min), 5 încercări și cooldown de 60s,
  dar nu există captcha; pentru trafic abuziv se recomandă unul.
- Imaginea originală nu este stocată, deci nu poate fi auditată conținutul după fapt.
- Adresele din `guest_trials` sunt date personale: păstrează doar emailul și momentul, iar
  la cerere șterge rândul (nu este folosit în alt scop decât limitarea trial-ului).
