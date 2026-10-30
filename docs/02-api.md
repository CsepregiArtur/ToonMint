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

### `POST /api/users`

Creează un utilizator (echivalentul public al înregistrării), fără a emite token.
`201 Created` cu `UserResponse`.

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

```json
[
  { "id": "anime_v1", "label": "Anime" },
  { "id": "cartoon_v2", "label": "Cartoon" },
  { "id": "pixel_v3", "label": "Pixel art" },
  { "id": "comic_v4", "label": "Comic book" }
]
```

Stilurile și prompturile lor sunt definite în `application.yml` → `app.jobs.styles`.

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
