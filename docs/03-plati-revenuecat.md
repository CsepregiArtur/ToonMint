# 03 – Plăți cu RevenueCat

RevenueCat este sursa de adevăr pentru plăți: găzduiește checkout-ul, validează bonul
(App Store / Google Play / Stripe pentru Web Billing) și trimite backendului un webhook.
Backendul **nu** procesează carduri și **nu** emite credite înainte de confirmare.

## 1. Modelul de integrare

```text
Vue / KMP ──POST /api/credits/purchase──► Backend ──► link pay.rev.cat/<token>/<UUID>
     │                                                        │
     └──────────────── checkout găzduit de RevenueCat ───────┘
                              │ plată confirmată
                              ▼
                    RevenueCat ──webhook──► POST /api/webhooks/revenuecat
                                                    │
                                       abonament activ / credite acordate
```

Identificatorul comun este **`users.public_id`** (UUID), trimis ca `app_user_id`:

- la Web Billing: parametru în linkul de checkout;
- la mobil: `Purchases.configure({ apiKey, appUserID: publicId })`.

Astfel, pentru același cont, webhook-urile din web și din aplicație ajung la aceeași
înregistrare din `users`.

## 2. Configurare în dashboard-ul RevenueCat

1. Creează un proiect nou și adaugă aplicațiile:
   - **Web Billing** (Stripe conectat din RevenueCat → *Integrations*);
   - **App Store** și **Google Play**, dacă apar versiuni mobile.
2. Creează produsele (identificatorii trebuie să coincidă cu `app.revenuecat.products`):

| Product ID | Tip în store | Preț | Credite |
|---|---|---|---|
| `toonmint_weekly_499` | abonament săptămânal | 4.99 USD | 50 |
| `toonmint_yearly_2499` | abonament anual | 24.99 USD | 300 |
| `toonmint_credits_20` | consumabil | 1.99 USD | 20 |

3. Creează entitlement-ul **`pro`** și atașează-i cele două abonamente.
4. Creează oferta `default` cu pachete pentru cele trei produse.
5. Pentru web: creează un **Web Purchase Link** către offering și copiază URL-ul
   de producție (`https://pay.rev.cat/<token>`). Backendul adaugă el app user id
   în path, deci nu lipi linkul complet în configurație decât dacă folosești
   placeholder-ul `{app_user_id}`.
6. (Opțional, recomandat) Notează **package id**-ul fiecărui pachet din offering
   (RevenueCat → Offerings → package) și pune-l în `package-id` mai jos: atunci
   linkul deschide direct produsul cerut, nu pagina cu toate pachetele.

## 3. Configurare backend

Valorile se pun în `.env` / variabile de mediu (profil `prod`):

```bash
REVENUECAT_WEBHOOK_AUTH_TOKEN=<token lung, aleator>
REVENUECAT_PURCHASE_LINK_URL=https://pay.rev.cat/<link-id>
REVENUECAT_ENTITLEMENT_ID=pro
```

`REVENUECAT_PURCHASE_LINK_URL` este **doar baza** linkului (fără query string).
Acceptă trei forme, în această ordine de prioritate:

| Configurație | Link generat |
|---|---|
| `https://pay.rev.cat/<token>` | `https://pay.rev.cat/<token>/<public_id>` |
| `https://pay.rev.cat/<token>/{app_user_id}` | placeholder-ul este înlocuit cu `<public_id>` |
| link complet care conține deja `app_user_id=` | folosit ca atare |

Dacă produsul are `package-id`, se adaugă `?package_id=<id>` ca să fie
preselectat pachetul corect.

Maparea produs → credite/abonament se face în `application.yml`:

```yaml
app:
  revenuecat:
    webhook-auth-token: ${REVENUECAT_WEBHOOK_AUTH_TOKEN:}
    purchase-link-base-url: ${REVENUECAT_PURCHASE_LINK_URL:}
    entitlement-id: ${REVENUECAT_ENTITLEMENT_ID:pro}
    products:
      - id: toonmint_weekly_499
        kind: SUBSCRIPTION
        subscription-type: WEEKLY
        credits: 50
        package-id: $rc_custom_weekly
      - id: toonmint_yearly_2499
        kind: SUBSCRIPTION
        subscription-type: YEARLY
        credits: 300
        package-id: $rc_custom_yearly
      - id: toonmint_credits_20
        kind: CREDIT_PACK
        credits: 20
        package-id: $rc_custom_credits
```

- Dacă un produs lipsește din listă, webhook-ul este marcat `IGNORED`.
- Dacă `REVENUECAT_PURCHASE_LINK_URL` lipsește, `POST /api/credits/purchase`
  răspunde `503 PAYMENTS_NOT_CONFIGURED`, iar catalogul returnează `configured=false`.
- Dacă `package-id` lipsește, linkul deschide pagina de selecție a pachetelor, iar
  utilizatorul poate cumpăra alt produs decât cel pe care a apăsat.

## 4. Configurare webhook

În RevenueCat → *Project settings → Integrations → Webhooks*:

| Câmp | Valoare |
|---|---|
| URL | `https://api.example.com/api/webhooks/revenuecat` |
| Authorization header | exact valoarea din `REVENUECAT_WEBHOOK_AUTH_TOKEN` |
| Environment | Production și/sau Sandbox |

Backendul compară tokenul în timp constant (`MessageDigest.isEqual`) și refuză cu `401`
oricărei cereri fără potrivire. Dacă tokenul nu este configurat, răspunde `503`
(comportament *fail closed*).

## 5. Evenimente tratate

| `event.type` | Efect |
|---|---|
| `INITIAL_PURCHASE` | activează abonamentul + acordă creditele lunii/săptămânii |
| `RENEWAL` | prelungește expirarea + acordă din nou creditele perioadei |
| `PRODUCT_CHANGE` | schimbă planul + acordă creditele noului plan |
| `UNCANCELLATION` | reactivează abonamentul (fără credite suplimentare) |
| `NON_RENEWING_PURCHASE` | acordă creditele pachetului (consumabil) |
| `EXPIRATION` | trece utilizatorul pe `FREE` și șterge data de expirare |
| `REFUND` | abonament → `FREE`; pachet de credite → marcat `IGNORED` (creditele deja consumate nu se retrag automat) |
| `CANCELLATION` | doar loghează; accesul rămâne până la expirare |
| `BILLING_ISSUE` | doar loghează |
| `TEST` | `IGNORED` (util la verificarea webhook-ului) |

Orice alt tip este salvat și marcat `IGNORED`, fără efect.

Fiecare plată (`INITIAL_PURCHASE`, `RENEWAL`, `PRODUCT_CHANGE`, `NON_RENEWING_PURCHASE`)
este înregistrată și în registrul financiar ca `INCOME / SALE`; `REFUND` devine o
cheltuială. Suma vine din `event.price`, cu fallback pe eticheta `price` a produsului
din configurație. Detalii: [10 – Bookkeeping](10-finante.md).

## 6. Idempotență și siguranță

1. Fiecare eveniment este inserat în `subscription_events` cu `event_id` **unic**.
2. Dacă RevenueCat retrimite același eveniment (retry după timeout), backendul
   răspunde `DUPLICATE` și **nu** mai acordă credite.
3. Acordarea de credite are în plus o a doua protecție: `credit_transactions`
   are index unic pe `external_reference`, iar `CreditService.grant` sare peste o
   referință deja existentă.
4. Tokenul de webhook trebuie să fie lung și aleator (minim 32 de caractere),
   stocat doar în secret manager / variabile de mediu.

## 7. Testarea webhook-ului local

Expune backendul public (ex. `ngrok http 8080`) sau folosește `curl`:

```bash
curl -X POST http://localhost:8080/api/webhooks/revenuecat \
  -H 'Authorization: tokenul-din-env' \
  -H 'Content-Type: application/json' \
  -d '{
    "api_version": "1.0",
    "event": {
      "id": "evt_test_001",
      "type": "NON_RENEWING_PURCHASE",
      "app_user_id": "<public_id-ul-utilizatorului>",
      "product_id": "toonmint_credits_20"
    }
  }'
```

Rezultat așteptat: `{"status":"PROCESSED","eventId":"evt_test_001","message":"Granted 20 credits"}`.
Aceeași comandă a doua oară trebuie să returneze `DUPLICATE`.

Pentru abonamente, adaugă `expiration_at_ms` (epoch millis) ca să verifici data de
expirare:

```json
{
  "api_version": "1.0",
  "event": {
    "id": "evt_test_002",
    "type": "INITIAL_PURCHASE",
    "app_user_id": "<public_id>",
    "product_id": "toonmint_weekly_499",
    "expiration_at_ms": 1793498400000
  }
}
```

## 8. Aplicația mobilă (viitorul client KMP)

Pe mobil, checkout-ul este gestionat de SDK-ul RevenueCat, nu de link:

```kotlin
Purchases.configure(PurchasesConfiguration.Builder(context, publicApiKey)
    .appUserID(user.publicId)
    .build())

// după achiziție
val customerInfo = Purchases.sharedInstance.purchase(packageToBuy, callback)
val isPro = customerInfo.entitlements["pro"]?.isActive == true
```

Backendul rămâne neschimbat: primește aceleași webhook-uri și actualizează
`subscription_type` / `credit_balance`.

## 9. Operare

- **Urmărire**: `GET /api/admin/revenuecat-events` arată ultimele webhook-uri și
  statusul lor; util când un client spune că „a plătit, dar nu are credite”.
- **Retrimitere**: din dashboard-ul RevenueCat poți retrimite un eveniment; datorită
  idempotenței nu vei dubla creditele.
- **Sandbox**: folosește tokenuri/chei separate pentru sandbox și producție; nu rula
  webhook-ul de sandbox pe baza de date de producție.
- **Reconciliere**: creditele pot fi verificate cu `SUM(amount)` din
  `credit_transactions` față de `users.credit_balance` (vezi
  [05 – Credite și rate limiting](05-credite-si-rate-limiting.md)).

## 10. Confirmarea plății în interfață

Frontendul nu așteaptă ca utilizatorul să apese „Reîmprospătează". După ce
checkout-ul este deschis, `creditsStore.startPaymentWatch` compară profilul
(`GET /api/users/me`) la fiecare 3 secunde, timp de maximum 5 minute, cu starea
dinaintea plății (sold + `subscriptionActive`):

- **confirmat** — soldul a crescut sau abonamentul s-a activat. Ambele se schimbă
  exclusiv prin webhook, deci întoarcerea din checkout fără plată nu produce
  fals pozitiv;
- **timeout** — mesaj care cere reîmprospătare manuală; RevenueCat retrimite
  evenimentul cu backoff până la ~80 de minute, iar idempotența garantează că
  creditele nu se dublează.

Starea dinaintea plății este păstrată în `sessionStorage`
(`toonmint.pendingPurchase`), deci urmărirea se reia și dacă pagina de succes
RevenueCat redirecționează înapoi în aplicație. Pentru asta configurează în
dashboard *Success → Redirect to a custom success page* către
`https://toonmint.cs-hub.xyz/credits?purchase=success`; `?purchase=cancel` oprește
urmărirea.
