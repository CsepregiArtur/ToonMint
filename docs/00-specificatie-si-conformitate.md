# 00 – Specificație și conformitate

Acest document urmărește cerințele din specificația inițială și arată unde este
implementată fiecare în cod.

## 1. Cerințe funcționale

| # | Cerință | Implementare | Stare |
|---|---|---|---|
| 1 | Server Java (Spring Boot 3) | `backend/pom.xml`, `com.toonmint.ToonMintApplication` | ✅ |
| 2 | Minim 3–4 tabele cu relații | `users`, `jobs`, `credit_transactions`, `subscription_events`, `email_messages` (Flyway `V1__init.sql`) | ✅ 5 tabele |
| 3 | 8 endpoint-uri CRUD | vezi secțiunea 3 din acest document | ✅ |
| 4 | 2 endpoint-uri cu custom query | `GET /api/users/{userId}/jobs?status=`, `GET /api/jobs/stats?from=&to=` | ✅ |
| 5 | Frontend Vue cu minim 4 componente | `JobUploader.vue`, `JobList.vue`, `CreditStore.vue`, `AdminPanel.vue` | ✅ |
| 6 | Minim 7 operații în frontend | vezi secțiunea 4 | ✅ |
| 7 | Client desktop/mobil cu minim 3 operații | endpoint-urile există; clientul KMP este pasul următor | ⏳ |
| 8 | Rate limiting cu sistem de credite | `RateLimitService` + `RedisTokenBucketRateLimiter` (Lua) + `CreditService` | ✅ |
| 9 | Fără stocare permanentă a imaginilor | `TempImageStore` (memorie/Redis, TTL 10 min) | ✅ |
| 10 | Retry / reconectare | backoff exponențial + jitter în `RetryExecutor`; clientul reinteroghează jobul | ✅ |
| 11 | API de generare imagini | `GeminiImageProvider` (Gemini 3.1 Flash Image) + `StubImageProvider` | ✅ |
| 12 | Plăți | RevenueCat (link de checkout + webhook) | ✅ |
| 13 | Email | Google/Gmail SMTP prin Spring Mail | ✅ |
| 14 | Autentificare JWT | `JwtService`, `JwtAuthenticationFilter`, Spring Security | ✅ |
| 15 | Documentație API | Swagger UI (`/swagger-ui.html`) + `docs/02-api.md` | ✅ |

## 2. Model de business

| Element | Valoare implementată |
|---|---|
| Abonament săptămânal | 4.99 USD → 50 credite |
| Abonament anual | 24.99 USD → 300 credite |
| Pachet suplimentar | 1.99 USD → 20 credite |
| Cost per generare | 1 credit |
| Cost AI estimat | ~0.034 USD / imagine (Gemini 3.1 Flash Image, 1K) |

Detaliile de marjă și calculul profitabilității sunt în
[05 – Credite și rate limiting](05-credite-si-rate-limiting.md).

## 3. Endpoint-uri CRUD și query-uri custom

| Cerință din specificație | Endpoint implementat |
|---|---|
| Creare utilizator | `POST /api/users` (și `POST /api/auth/register`) |
| Detalii utilizator | `GET /api/users/{id}`, `GET /api/users/me` |
| Actualizare profil | `PUT /api/users/{id}` |
| Creare job | `POST /api/jobs` (multipart sau JSON base64) |
| Status job | `GET /api/jobs/{id}` |
| Actualizare status | `PUT /api/jobs/{id}/status` (operator) |
| Ștergere job | `DELETE /api/jobs/{id}` (ștergere logică `REMOVED`) |
| Cumpărare credite | `POST /api/credits/purchase` |
| Job-uri după status (custom query) | `GET /api/users/{userId}/jobs?status=COMPLETED` |
| Statistici agregate (custom query) | `GET /api/jobs/stats?from=&to=` |
| Retry job | `POST /api/jobs/{id}/retry` |
| Descărcare imagine | `GET /api/jobs/{id}/download` |
| Trimitere email | `POST /api/admin/send-email` |
| Căutare după cuvânt cheie | `GET /api/jobs/search?keyword=` |
| Webhook plăți | `POST /api/webhooks/revenuecat` |

## 4. Operații în frontend

| # | Operație | Unde |
|---|---|---|
| 1 | Creare job (upload + generare) | `JobUploader.vue` → `jobsStore.createJob` |
| 2 | Listă job-uri proprii + status | `JobList.vue` → `jobsStore.loadMine` |
| 3 | Filtrare după status (custom query) | `JobList.vue` (selector) |
| 4 | Descărcare imagine | `JobList.vue` → `jobsStore.download` |
| 5 | Cumpărare credite / abonament | `CreditStore.vue` → `creditsStore.purchase` |
| 6 | Ștergere job | `JobList.vue` → `jobsStore.remove` |
| 7 | Istoric tranzacții | `CreditStore.vue` → `creditsStore.loadTransactions` |
| 8 | Căutare job-uri | `GET /api/jobs/search` (disponibil și pentru clientul mobil) |
| 9 | Aprobare/ștergere de operator | `AdminPanel.vue` → `adminStore.updateStatus` |
| 10 | Trimitere email către utilizatori | `AdminPanel.vue` → `adminStore.sendEmail` |

## 5. Operații pentru viitorul client desktop/mobil

Serverul expune deja tot ce este necesar pentru KMP:

1. căutare job-uri: `GET /api/jobs/search?keyword=...`;
2. schimbare status: `PUT /api/jobs/{id}/status`;
3. trimitere email: `POST /api/admin/send-email`.

Pentru plăți în aplicația mobilă, clientul folosește SDK-ul RevenueCat (App Store /
Google Play), iar backendul primește aceleași webhook-uri — vezi
[03 – Plăți RevenueCat](03-plati-revenuecat.md).

## 6. Diferențe față de specificație

- Ștergerea unui job este **logică** (`status = REMOVED`), pentru a păstra
  trasabilitatea în `credit_transactions` (cheie străină către `jobs`).
- Retry-ul este permis doar cât timp fotografia originală mai există în cache (TTL).
  După expirare, utilizatorul trebuie să încarce din nou poza — comportamentul este
  raportat explicit prin `410 SOURCE_IMAGE_EXPIRED`.
- Providerul alternativ OpenAI GPT Image nu este implementat, dar interfața
  `ImageGenerationProvider` permite adăugarea lui fără modificări în restul aplicației.
