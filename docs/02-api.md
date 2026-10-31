# 02 – API

- Base URL local: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Autentificare: `Authorization: Bearer <JWT>` (obținut din `/api/auth/login`)

## 1. Convenții

### Format de eroare

Toate erorile au același corp:

```json
{
  "timestamp": "2026-10-30T19:30:00Z",
  "status": 402,
  "code": "INSUFFICIENT_CREDITS",
  "message": "Not enough credits. Buy more credits to continue.",
  "details": { "balance": 0, "required": 1, "purchaseEndpoint": "/api/credits/purchase" }
}
```

| Status | Când apare |
|---|---|
| `400` | validare eșuată, stil necunoscut, imagine invalidă |
| `401` | lipsă/expirare token sau credențiale greșite |
| `402` | credite insuficiente (`INSUFFICIENT_CREDITS`) |
| `403` | acces la resursa altui utilizator sau zonă de operator |
| `404` | resursa nu există |
| `409` | conflict (email duplicat, job care nu poate fi reîncercat) |
| `410` | imaginea temporară a expirat (`IMAGE_EXPIRED`, `SOURCE_IMAGE_EXPIRED`) |
| `413` | imagine peste limita de dimensiune |
| `429` | rate limit depășit; răspunsul conține header `Retry-After` |
| `402 TRIAL_ALREADY_USED` | adresa a folosit deja imaginea gratuită |
| `503` | plăți neconfigurate pe mediul curent |

### Paginare

Endpoint-urile de listare acceptă `page` (0-based) și `size` (max 100) și returnează:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalItems": 0,
  "totalPages": 0,
  "hasNext": false
}
```

## 2. Autentificare

### `POST /api/auth/register`

```json
{ "email": "ana@example.com", "password": "parola-mea-lunga", "displayName": "Ana" }
```

`201 Created`

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "expiresAt": "2026-10-31T19:30:00Z",
  "user": {
    "id": 1,
    "publicId": "0b1f2f7e-6b0e-4e9f-9d55-0a1b2c3d4e5f",
    "email": "ana@example.com",
    "displayName": "Ana",
    "creditBalance": 0,
    "subscriptionType": "FREE",
    "subscriptionActive": false,
    "subscriptionExpiresAt": null,
    "role": "USER",
    "createdAt": "2026-10-30T19:30:00Z"
  }
}
```

### `POST /api/auth/login`

```json
{ "email": "ana@example.com", "password": "parola-mea-lunga" }
```

Returnează același format ca la register. `401 INVALID_CREDENTIALS` la date greșite.

### Login demo (doar în dezvoltare)

`GET /api/auth/demo-login` → `{ "available": true, "accounts": [...] }`

`POST /api/auth/demo-login` cu `{}` (cont demo) sau `{ "role": "ADMIN" }` (operator) →
același `AuthResponse` ca la login. Endpoint-ul nu există când `app.demo.enabled=false`
(profilul `prod`).

### `POST /api/users`

Creează un utilizator (echivalentul public al înregistrării), fără a emite token.
`201 Created` cu `UserResponse`.

## 2b. Generare gratuită (fără cont)

Nu necesită token. Identificatorul public al jobului (`UUID`) funcționează ca token de acces.

### `GET /api/public/trial/eligibility?email=...`

```json
{ "eligible": true }
```

### `POST /api/public/trial` (multipart)

```bash
curl -X POST http://localhost:8080/api/public/trial \
  -F "email=tu@exemplu.com" \
  -F "style_id=anime_v1" \
  -F "image=@photo.png"
```

`202 Accepted` cu `JobResponse` (`trial: true`, `creditsConsumed: 0`, `userId: null`).
Varianta JSON acceptă `{ "email": "...", "styleId": "...", "imageBase64": "..." }`.

Erori:

| Status | Cod | Când |
|---|---|---|
| `400` | `BAD_REQUEST` | email invalid, imagine lipsă/nesuportată, stil necunoscut |
| `402` | `TRIAL_ALREADY_USED` | adresa a primit deja imaginea gratuită |
| `413` | `IMAGE_TOO_LARGE` | imagine peste limita configurată |
| `429` | `RATE_LIMIT_EXCEEDED` | prea multe cereri de pe aceeași adresă sau IP |

Răspunsul pentru `TRIAL_ALREADY_USED` include `details.subscribeEndpoint`
(`/billing/catalog`) și `details.registerEndpoint` (`/auth/register`).

### `GET /api/public/jobs/{publicId}`

Statusul jobului gratuit. `404 NOT_FOUND` dacă identificatorul nu există.

### `GET /api/public/jobs/{publicId}/download`

Imaginea generată, cât timp mai există în cache (TTL 10 minute).
`410 IMAGE_EXPIRED` după expirare, `409 JOB_NOT_COMPLETED` înainte de finalizare.

## 3. Utilizatori

| Metodă | Path | Acces | Descriere |
|---|---|---|---|
| `GET` | `/api/users/me` | autentificat | profilul curent |
| `GET` | `/api/users/{id}` | proprietar/operator | detalii utilizator |
| `PUT` | `/api/users/{id}` | proprietar/operator | actualizare `displayName` / `email` |
| `GET` | `/api/users/{userId}/jobs?status=&page=&size=` | proprietar/operator | **custom query**: job-urile unui utilizator, filtrabile după status |
| `GET` | `/api/users/{userId}/transactions?page=&size=` | proprietar/operator | istoricul de credite al utilizatorului |

Exemplu `PUT /api/users/1`:

```json
{ "displayName": "Ana Maria", "email": "ana.maria@example.com" }
```

## 4. Stiluri

### `GET /api/styles`

Returnează toate stilurile disponibile (26 în configurația livrată), sortate în ordinea
din `application.yml`. Fiecare intrare are `id`, `label` și `previewUrl` — imaginea de
exemplu a rezultatului în acel stil (servită de frontend, prefix configurabil prin
`app.jobs.style-preview-base-url`):

```json
[
  { "id": "anime_v1", "label": "Anime", "previewUrl": "/demo/styles/anime_v1.jpg" },
  { "id": "manga_bw", "label": "Manga (B&W)", "previewUrl": "/demo/styles/manga_bw.jpg" },
  { "id": "chibi", "label": "Chibi", "previewUrl": "/demo/styles/chibi.jpg" },
  { "id": "webtoon", "label": "Webtoon", "previewUrl": "/demo/styles/webtoon.jpg" },
  { "id": "cartoon_v2", "label": "Cartoon", "previewUrl": "/demo/styles/cartoon_v2.jpg" },
  { "id": "retro_90s", "label": "90s cartoon", "previewUrl": "/demo/styles/retro_90s.jpg" },
  { "id": "comic_v4", "label": "Comic book", "previewUrl": "/demo/styles/comic_v4.jpg" },
  { "id": "caricature", "label": "Caricature", "previewUrl": "/demo/styles/caricature.jpg" },
  { "id": "storybook", "label": "Storybook", "previewUrl": "/demo/styles/storybook.jpg" },
  { "id": "pixel_v3", "label": "Pixel art", "previewUrl": "/demo/styles/pixel_v3.jpg" },
  { "id": "vector_flat", "label": "Flat vector", "previewUrl": "/demo/styles/vector_flat.jpg" },
  { "id": "pop_art", "label": "Pop art", "previewUrl": "/demo/styles/pop_art.jpg" },
  { "id": "low_poly", "label": "Low poly", "previewUrl": "/demo/styles/low_poly.jpg" },
  { "id": "isometric", "label": "Isometric 3D", "previewUrl": "/demo/styles/isometric.jpg" },
  { "id": "claymation", "label": "Claymation", "previewUrl": "/demo/styles/claymation.jpg" },
  { "id": "lego", "label": "Brick figure", "previewUrl": "/demo/styles/lego.jpg" },
  { "id": "watercolor", "label": "Watercolor", "previewUrl": "/demo/styles/watercolor.jpg" },
  { "id": "oil_painting", "label": "Oil painting", "previewUrl": "/demo/styles/oil_painting.jpg" },
  { "id": "pencil_sketch", "label": "Pencil sketch", "previewUrl": "/demo/styles/pencil_sketch.jpg" },
  { "id": "ink_lineart", "label": "Ink line art", "previewUrl": "/demo/styles/ink_lineart.jpg" },
  { "id": "paper_cut", "label": "Paper cutout", "previewUrl": "/demo/styles/paper_cut.jpg" },
  { "id": "graffiti", "label": "Graffiti", "previewUrl": "/demo/styles/graffiti.jpg" },
  { "id": "cyberpunk", "label": "Cyberpunk", "previewUrl": "/demo/styles/cyberpunk.jpg" },
  { "id": "vaporwave", "label": "Vaporwave", "previewUrl": "/demo/styles/vaporwave.jpg" },
  { "id": "fantasy", "label": "Fantasy illustration", "previewUrl": "/demo/styles/fantasy.jpg" },
  { "id": "renaissance", "label": "Renaissance portrait", "previewUrl": "/demo/styles/renaissance.jpg" }
]
```

Stilurile, prompturile și prefixul imaginilor de exemplu sunt definite în `application.yml`
→ `app.jobs.styles` / `app.jobs.style-preview-base-url`.

#### Structura unui prompt de stil

Fiecare prompt urmează aceeași schemă, ca rezultatele să fie previzibile între stiluri:

1. **subiect + mediu** — „Create a <stil> image from the uploaded subject.”;
2. **tehnica de linie și umbră** — tip de contur, hașură, cel shading, screentone;
3. **paletă și lumină** — culori, contrast, direcția luminii;
4. **anatomie și expresie** — proporții, trăsături, grad de exagerare;
5. **mediul** — cum se transformă fundalul și obiectele;
6. **compoziție și ton** — energie, cadru, plus „fully stylized, non-realistic world”.

Fotografia încărcată este trimisă modelului ca imagine de referință (`inlineData`), deci
promptul descrie doar transformarea, nu conținutul pozei. Exemplu (stilul `anime_v1`):

```text
Create a trending anime art style image from the uploaded subject. Use confident
line-work with slight variation and minimal cel shading using flat shadow shapes.
Use bright, saturated colors and clean graphic lighting. ... Composition and tone
should be energetic, lively, and comedic in a fully stylized, non-realistic world.
``` Dacă prefixul este gol,
`previewUrl` este `null` și UI-ul afișează doar eticheta.
`style_id` trimis la creare trebuie să fie unul dintre id-urile acestei liste; altfel
răspunsul este `400 BAD_REQUEST`. Adăugarea unui stil nou nu necesită modificări de cod —
doar o intrare nouă în configurație (id, `label`, `prompt`).

## 5. Job-uri

### `POST /api/jobs` (multipart)

```bash
curl -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -F "style_id=anime_v1" \
  -F "image=@photo.png"
```

Aceiași endpoint acceptă și `image_base64` ca parametru de formular.

### `POST /api/jobs` (JSON)

```json
{ "styleId": "cartoon_v2", "imageBase64": "data:image/png;base64,iVBORw0KGgo..." }
```

Ambele variante răspund `202 Accepted`:

```json
{
  "id": 42,
  "publicId": "9e2f...",
  "userId": 1,
  "guestEmail": null,
  "trial": false,
  "styleId": "anime_v1",
  "styleLabel": "Anime",
  "status": "PENDING",
  "creditsConsumed": 1,
  "attempts": 0,
  "errorMessage": null,
  "createdAt": "2026-10-30T19:30:00Z",
  "startedAt": null,
  "completedAt": null
}
```

Efecte: se consumă `app.jobs.credits-per-job` credite (implicit 1), se aplică rate
limiting-ul (429), iar jobul este preluat de worker.

### `GET /api/jobs/{id}`

Returnează `JobResponse`. Stări posibile: `PENDING`, `PROCESSING`, `COMPLETED`,
`FAILED`, `REMOVED`.

### `GET /api/jobs/{id}/download`

- `200 OK`, `Content-Type: image/png` (sau `image/jpeg`), `Content-Disposition: attachment`
- `409 JOB_NOT_COMPLETED` dacă jobul nu este finalizat
- `410 IMAGE_EXPIRED` dacă imaginea a expirat din cache (TTL 10 minute)

### `GET /api/jobs/{id}` – reconectare

Dacă conexiunea clientului cade, jobul continuă pe server. Clientul reinteroghează
`GET /api/jobs/{id}`; frontend-ul face polling automat până la `COMPLETED`/`FAILED`.

### `POST /api/jobs/{id}/retry`

Permis doar pentru job-uri `FAILED`. Consumă un credit nou și reprogramează jobul.

- `202 Accepted` — job reprogramat
- `409 JOB_NOT_RETRYABLE` — alt status
- `410 SOURCE_IMAGE_EXPIRED` — fotografia originală nu mai e în cache
- `402 INSUFFICIENT_CREDITS` — sold insuficient

### `DELETE /api/jobs/{id}`

`204 No Content`. Marchează jobul `REMOVED` și elimină imaginea din cache.
Disponibil pentru proprietar și operator.

### `PUT /api/jobs/{id}/status` (operator)

```json
{ "status": "REMOVED" }
```

- `COMPLETED` — marchează finalizat
- `FAILED` — marchează eșuat și **rambursează** creditul (o singură dată)
- `REMOVED` — ștergere logică + curățarea cache-ului

### `GET /api/jobs` (operator)

Listează toate job-urile: `?status=COMPLETED&page=0&size=50`.

### `GET /api/jobs/search?keyword=&page=&size=`

Caută în `style_id`, `status`, `error_message` și emailul proprietarului.
Utilizatorii obișnuiți văd doar propriile job-uri.

### `GET /api/jobs/stats?from=2026-10-01T00:00:00Z&to=2026-11-01T00:00:00Z` (operator)

```json
{
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-11-01T00:00:00Z",
  "totalJobs": 128,
  "completed": 120,
  "failed": 8,
  "successRate": 0.9375,
  "creditsConsumed": 128,
  "byStyle": [
    { "styleId": "anime_v1", "jobs": 70, "completed": 66, "failed": 4, "credits": 70 }
  ]
}
```

## 6. Credite și plăți

### `GET /api/billing/catalog`

```json
{
  "configured": true,
  "entitlementId": "pro",
  "plans": [
    {
      "id": "toonmint_weekly_499",
      "kind": "SUBSCRIPTION",
      "label": "Weekly plan (50 generations)",
      "price": "$4.99 / week",
      "credits": 50,
      "purchaseUrl": "https://pay.rev.cat/abc123?app_user_id=...&product_id=toonmint_weekly_499"
    }
  ],
  "packs": [
    {
      "id": "toonmint_credits_20",
      "kind": "CREDIT_PACK",
      "label": "20 extra generations",
      "price": "$1.99",
      "credits": 20,
      "purchaseUrl": "https://pay.rev.cat/abc123?app_user_id=...&product_id=toonmint_credits_20"
    }
  ]
}
```

### `POST /api/credits/purchase`

```json
{ "packId": "toonmint_credits_20" }
```

Returnează `CreditPackResponse` cu `purchaseUrl`. Creditele sunt acordate **numai**
după ce RevenueCat trimite webhook-ul `NON_RENEWING_PURCHASE`.
Dacă `REVENUECAT_PURCHASE_LINK_URL` nu este setat → `503 PAYMENTS_NOT_CONFIGURED`.

### `GET /api/credits/transactions?page=&size=`

```json
{
  "items": [
    {
      "id": 10,
      "amount": -1,
      "type": "CONSUMPTION",
      "referenceJobId": 42,
      "provider": null,
      "description": "Generation for job #42",
      "createdAt": "2026-10-30T19:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalItems": 1,
  "totalPages": 1,
  "hasNext": false
}
```

## 7. Webhook RevenueCat

### `POST /api/webhooks/revenuecat`

Header obligatoriu: `Authorization: <REVENUECAT_WEBHOOK_AUTH_TOKEN>`.
Payload-ul este cel trimis de RevenueCat (`{ "api_version": "1.0", "event": { ... } }`).

Răspuns `200`:

```json
{ "status": "PROCESSED", "eventId": "evt_123", "message": "Granted 20 credits" }
```

| `status` | Semnificație |
|---|---|
| `PROCESSED` | efect aplicat (abonament activat / credite acordate / expirare) |
| `IGNORED` | eveniment cunoscut, dar fără efect (ex. `CANCELLATION`, produs necunoscut) |
| `DUPLICATE` | `event_id` deja procesat — nu se aplică din nou |
| `FAILED` | `app_user_id` necunoscut sau eroare internă |

Detalii de configurare: [03 – Plăți RevenueCat](03-plati-revenuecat.md).

## 8. Administrare (doar `ADMIN`)

### `POST /api/admin/send-email`

```json
{
  "userIds": [1, 2],
  "emails": ["client@example.com"],
  "subject": "Noutăți ToonMint",
  "text": "Salut! Am adăugat un stil nou."
}
```

```json
{ "requested": 3, "sent": 3, "failed": 0, "simulated": false, "failures": [] }
```

`simulated = true` când `MAIL_ENABLED=false` (util în dezvoltare, fără trimitere reală).

### `GET /api/admin/users?page=&size=`

Listă de utilizatori pentru selectarea destinatarilor.

### `GET /api/admin/style-previews`

```json
{
  "provider": "gemini:gemini-3.1-flash-image",
  "outputDir": "/.../frontend/public/demo/styles",
  "source": "/.../frontend/public/demo/before.jpg",
  "present": ["anime_v1"],
  "missing": ["manga_bw", "chibi"],
  "generativeProvider": true
}
```

### `POST /api/admin/style-previews/regenerate?missingOnly=true&limit=0`

Generează exemplele lipsă (sau pe cele din body) cu providerul AI configurat și le
scrie ca `<style-id>.jpg` în folderul servit de frontend.

```json
{ "provider": "gemini:gemini-3.1-flash-image",
  "outputDir": "/.../frontend/public/demo/styles",
  "generated": ["anime_v1", "manga_bw"],
  "failed": [] }
```

- `missingOnly` (implicit `true`) — sare peste cele existente;
- `limit` — numărul maxim de imagini într-o rulare (0 = toate);
- body opțional: `{ "styles": ["anime_v1"] }`;
- `409 PREVIEW_REQUIRES_AI_PROVIDER` dacă `AI_PROVIDER=stub`;
- `409 PREVIEW_SOURCE_MISSING` dacă lipsește fotografia sursă.

### `GET /api/admin/revenuecat-events?page=&size=`

Ultimele webhook-uri primite, cu statusul procesării — util la depanare.

## 9. Exemple rapide

```bash
# înregistrare + token
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"demo@example.com","password":"parola-demo-123","displayName":"Demo"}' \
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["token"])')

# acordare manuală de credite în dezvoltare (SQL, profil dev H2)
# INSERT INTO credit_transactions ...; UPDATE users SET credit_balance = 10 WHERE email = 'demo@example.com';

# generare
curl -X POST http://localhost:8080/api/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -F "style_id=anime_v1" -F "image=@photo.png"

# status
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/jobs/1
```
