# 12 – Verificare de lansare (audit live pe server)

**Data:** 2026-10-31
**Mediu:** producție — `https://toonmint.cs-hub.xyz`
**Versiune testată:** `1.0.0` (commit `fe9fc34`, tag `v1.0.0`)
**Tip audit:** read-only (fără creare de conturi, fără plăți, fără generări AI)

---

## Verdict

> **NU este încă „ready for launch".**
> Aplicația funcționează, dar există **1 blocker de securitate** și **5 probleme majore**
> care trebuie rezolvate înainte de trafic real.

| Categorie | Număr |
|---|---|
| 🔴 Blocker | 1 |
| 🟠 Major | 5 |
| 🟡 Minor | 6 |
| ⏳ Netestate (necesită cont/plată) | 6 |

---

## 1. Infrastructură & disponibilitate

| Verificare | Rezultat | Status |
|---|---|---|
| DNS `toonmint.cs-hub.xyz` | rezolvă | ✅ |
| TLS | `CN=cs-hub.xyz`, Google Trust Services WE1, valid 10 Sep → 9 Dec 2026 | ✅ |
| HTTP/2 + HTTP/3 (`alt-svc: h3`) | active | ✅ |
| `www.toonmint.cs-hub.xyz` | **nu rezolvă** (status 000) | 🟡 |
| HTTP → HTTPS redirect | netestabil cu curl (vezi 1.1) | ⚠️ |
| Timp de răspuns landing | DOMContentLoaded **99 ms**, load **130 ms** | ✅ |

### 1.1 Cloudflare blochează toți clienții non-browser

Orice request făcut cu `curl` (inclusiv cu `User-Agent` de Chrome) primește:

```
HTTP/2 403
server: cloudflare
content-type: text/plain
Your request was blocked.
```

Un browser real trece. Blocajul se bazează pe fingerprint TLS, nu pe User-Agent.

**Impact:**
- ✅ Webhook-ul RevenueCat **a fost deblocat** printr-o excepție WAF pe `/api/webhooks/revenuecat` — verificat `200`.
- ❌ Orice monitorizare uptime (UptimeRobot, BetterStack, Pingdom) va raporta **DOWN** permanent.
- ❌ Viitoare integrări server-to-server (Google OAuth callback, alte webhook-uri) vor primi 403.
- ⚠️ Scanner-ele de linkuri din email (Gmail/Outlook) pot primi 403 la click pe linkul de verificare.

**Recomandare:** regulă WAF `Skip` pentru toate rutele `/api/*` (nu doar webhook), sau o listă de `User-Agent`-uri permise pentru monitorizare.

---

## 2. Securitate

| Verificare | Rezultat | Status |
|---|---|---|
| Header-e de securitate pe **API** (`/api/*`) | CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, `Permissions-Policy` | ✅ |
| Header-e de securitate pe **HTML/SPA** | **toate lipsesc** | 🟠 |
| `Strict-Transport-Security` (HSTS) | **lipsește peste tot** (API + HTML) | 🟠 |
| Endpoint-uri protejate fără token | `401` JSON uniform | ✅ |
| Validare request-uri | `400 VALIDATION_ERROR` cu detalii pe câmp | ✅ |
| Enumerare utilizatori la login | același răspuns `INVALID_CREDENTIALS` | ✅ |
| CSRF | dezactivat corect (API stateless cu JWT) | ✅ |
| Rate limiting pe signup / OTP / reset | implementat (`signup-email:`, `otp-ip:` etc.) | ✅ |
| **Rate limiting pe `/api/auth/login`** | **inexistent** | 🔴 |
| Source maps în bundle | nu sunt expuse | ✅ |
| Swagger / OpenAPI public | mascat de fallback-ul SPA (nu e expus) | ✅ |

### 🔴 B1. Login fără rate limiting (BLOCKER)

`AuthService.login()` nu apelează `rateLimitService.check(...)`. Limiter-ul există și e funcțional,
dar e aplicat doar pe `signup-email`, `signup-ip`, `otp-email`, `otp-ip`.

**Dovadă:** 15 încercări de login eșuate în ~0,7 s → **15 × `401`, zero `429`**.

```
[401,401,401,401,401,401,401,401,401,401,401,401,401,401,401]
```

Un atacator poate face credential stuffing / brute force. BCrypt încetinește la ~100 ms/încercare,
adică ~10 încercări/secundă/IP — trivial de paralelizat.

**Fix:** în `AuthService.login()`, la început:

```java
rateLimitService.check("login-ip:" + ipKey(clientIp));
rateLimitService.check("login-email:" + normalizeEmail(request.email()));
```

Necesită adăugarea parametrului `clientIp` în semnătura metodei și în `AuthController.login`.

### 🟠 M1. Zero header-e de securitate pe HTML

`GET /` returnează doar `content-type: text/html` și `cache-control: no-cache`.
Lipsesc: `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`,
`Referrer-Policy`, `Permissions-Policy`.

Aplicația poate fi încadrată în iframe (clickjacking). CSP-ul există doar pe răspunsurile API,
unde e aproape inutil — el trebuie să fie pe documentul HTML.

**Fix:** în `nginx`, în `location /`:

```nginx
add_header X-Frame-Options "DENY" always;
add_header X-Content-Type-Options "nosniff" always;
add_header Referrer-Policy "strict-origin-when-cross-origin" always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
```

CSP-ul e mai delicat (bundle-ul Vue folosește `unsafe-inline` pentru stiluri) — pornește în
`Content-Security-Policy-Report-Only` și strânge rapoarte o săptămână.

### 🟠 M2. HSTS lipsește

Fără `Strict-Transport-Security`, prima cerere poate fi interceptată (SSL-strip) înainte de redirect.

**Fix:** în Cloudflare → SSL/TLS → Edge Certificates → **Enable HSTS** (max-age 6 luni, fără `preload` la început).

---

## 3. SEO & meta

| Verificare | Rezultat | Status |
|---|---|---|
| `<title>` | `ToonMint — photo to cartoon` | ✅ |
| `<link rel="icon">` | `/toonmint-mark.svg` (+ apple-touch-icon) | ✅ |
| `<meta name="description">` | **lipsește** | 🟠 |
| Open Graph (`og:title`, `og:image`, `og:description`) | **lipsesc** | 🟠 |
| `robots.txt` | returnează **HTML** (index.html), nu text | 🟠 |
| `sitemap.xml` | returnează **HTML** (index.html) | 🟠 |
| Rută 404 (SPA) | **nu există** — URL necunoscut → redirect la `/login` | 🟠 |
| `lang="ro"` în `index.html` | conținutul implicit poate fi EN pentru vizitatori străini | 🟡 |

### 🟠 M3. `robots.txt` și `sitemap.xml` returnează HTML

`nginx` trimite tot ce nu e `/api/*` către `index.html`:

```
GET /robots.txt   → 200  text/html  <!doctype html>...
GET /sitemap.xml  → 200  text/html  <!doctype html>...
```

Google Search Console va raporta eroare de parsare. Fără `robots.txt` valid și fără sitemap,
indexarea e lentă și imprevizibilă — problemă pentru un site care depinde de trafic organic și AdSense.

**Fix:** fișiere statice reale în `frontend/public/robots.txt` și `sitemap.xml` (lista de rute publice:
`/`, `/login`, `/legal/*`).

### 🟠 M4. Nu există pagină 404

`frontend/src/router.js` nu are rută catch-all. Un URL inexistent ajunge la:

```
GET /this-page-does-not-exist → 200 → redirect la /login?redirect=/this-page-does-not-exist
```

Consecințe: soft-404 (200 pentru conținut inexistent), utilizator confuz, linkuri moarte
indexate de motoare.

**Fix:** adaugă `{ path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView, meta: { public: true } }`.

### 🟠 M5. Lipsesc meta description și Open Graph

Fără `description` și `og:*`, share-urile pe social/WhatsApp arată fără imagine și fără text,
iar Google afișează un snippet generat arbitrar.

**Fix:** în `frontend/index.html` adaugă `description`, `og:title`, `og:description`,
`og:image` (poate fi `toonmint-logo-wordmark.png` sau un `demo/after.jpg`), `twitter:card`.

---

## 4. API — funcțional

| Endpoint | Metodă | Rezultat | Status |
|---|---|---|---|
| `/api/styles` | GET | `200`, listă de stiluri din DB | ✅ |
| `/api/public/billing/catalog` | GET | `200`, 3 planuri cu prețuri reale din config | ✅ |
| `/api/billing/catalog` | GET | `401` | ✅ |
| `/api/users/me` | GET | `401` | ✅ |
| `/api/jobs` | GET | `401` | ✅ |
| `/api/jobs/stats` | GET | `401` | ✅ |
| `/api/admin/users` | GET | `401` | ✅ |
| `/api/credits/transactions` | GET | `401` | ✅ |
| `/api/credits/purchase` | POST | `401` | ✅ |
| `/api/public/trial/eligibility` | GET | `200 {"eligible":true}` | 🟡 |
| `/api/auth/login` (parolă greșită) | POST | `401 INVALID_CREDENTIALS` | ✅ |
| `/api/auth/login` (email invalid) | POST | `400 VALIDATION_ERROR` | ✅ |
| `/api/auth/register` (payload invalid) | POST | `400` + detalii câmpuri | ✅ |
| `/api/auth/forgot-password` (email invalid) | POST | `400` | ✅ |
| `/api/nonexistent` | GET | `401` (ar trebui `404`) | 🟡 |
| `/api/webhooks/revenuecat` (fără token) | POST | `401 INVALID_WEBHOOK_SIGNATURE` | ✅ |
| **`/api/webhooks/revenuecat` (test RevenueCat)** | POST | **`200 IGNORED "Test event received"`** | ✅ |

**Concluzie:** contractul API, securitatea rutelor și validarea sunt corecte.
Baza de date răspunde (stilurile vin din DB), iar catalogul de plăți e aliniat la RevenueCat.

### 🟡 m1. `/api/public/trial/eligibility` permite enumerare de emailuri

Răspunde `{"eligible":false}` pentru un email care a folosit deja trialul → se poate afla
dacă o adresă are cont. Impact mic, dar merită mutat pe un token opac.

### 🟡 m2. `/api/nonexistent` → `401` în loc de `404`

`anyRequest().authenticated()` prinde și rutele inexistente. Clientul primește „Authentication required"
pentru un endpoint care nu există. Corectează cu un `ResourceHttpRequestHandler` / `NoHandlerFoundException`
mapat la `404`, sau acceptă ca atare.

---

## 5. Frontend

| Verificare | Rezultat | Status |
|---|---|---|
| Landing se încarcă | `200`, hero + slider before/after + prețuri din API | ✅ |
| Erori JS la încărcare | **niciuna** | ✅ |
| Request-uri eșuate la încărcare | **niciunul** | ✅ |
| Assets hash-uite + `immutable` 1 an | `/assets/index-*.js`, `.css` | ✅ |
| Compresie | `gzip` pe JS/CSS/SVG | ✅ |
| `index.html` | `no-cache, must-revalidate` (corect pentru SPA) | ✅ |
| Pagini legale | `/legal/termeni` etc. randează ~6,8 KB text | ✅ |
| i18n | 20 limbi, comutare funcțională, `<html lang>` actualizat | ✅ |
| Banner consimțământ + setări granulare | prezent | ✅ |
| Linkuri ANPC / ODR / contact | prezente în footer | ✅ |
| Versiune în footer | `ToonMint v1.0.0` — deploy-ul a preluat bump-ul | ✅ |
| `/favicon.ico` | returnează HTML (browserul folosește SVG-ul declarat) | 🟡 |
| Analytics (GTM / GA4 / AdSense) | **neconfigurate** — variabilele `VITE_*` sunt goale | 🟡 |

### 🟡 m3. Analytics complet dezactivate

`index.html` nu conține GTM/GA4/AdSense. Conform `frontend/.env.example`, e comportamentul
așteptat când `VITE_GTM_ID`, `VITE_GA4_ID`, `VITE_ADSENSE_CLIENT` sunt goale.

Dacă lansarea presupune monetizare prin AdSense sau măsurare, trebuie setate **înainte** de build.

---

## 6. Plăți (RevenueCat) — configurare verificată

| Element | Valoare | Status |
|---|---|---|
| Web config | `ToonMint (RevenueCat Billing)` → `app7b726b401d` | ✅ |
| Stripe | `acct_1SXMNGBuzqtQc7ep` conectat | ✅ |
| Produse | `toonmint_weekly_499` ($4.99/săpt, 50 cr), `toonmint_yearly_2499` ($24.99/an, 300 cr), `toonmint_credits_20` ($1.99, 20 cr, consumabil) | ✅ |
| Entitlement | `pro` ← weekly + yearly | ✅ |
| Offering | `default` cu 3 pachete (`$rc_weekly`, `$rc_annual`, `credits_20`) | ✅ |
| Purchase link | `https://pay.rev.cat/torxtsegooxbshvr` | ✅ |
| Checkout live | EN, logo + culori de brand, 3 produse afișate | ✅ |
| Webhook | `ToonMint backend (prod)` → toate evenimentele, Production + Sandbox | ✅ |
| Webhook delivery | test event → **`200`** | ✅ |
| Mapare produs → credite | `application.yml` aliniat la ID-urile reale | ✅ |

### ⚠️ Necesită verificare manuală
- Cumpărare reală cu card test `4242 4242 4242 4242` → creditele ajung în DB (`users.credit_balance` +N)
- Randarea documentului de confirmare înainte de checkout (`PurchaseAcknowledgement`)
- Taxele (Stripe Tax e **dezactivat** — vezi `docs/11-fiscalitate-si-tva.md`)

---

## 7. Ce NU a putut fi testat (necesită cont sau cost)

| Flux | Motiv | Prioritate testare |
|---|---|---|
| Register → verificare email → login | necesită cont real + SMTP | 🔴 înainte de lansare |
| Upload imagine → generare AI → download | consumă credite Gemini | 🔴 înainte de lansare |
| Trial gratuit (guest) | consumă credite Gemini | 🟠 |
| Plată end-to-end (webhook → credite) | necesită card | 🔴 |
| Trimitere email (SMTP Google) | ar trimite email real | 🟠 |
| Panou admin | necesită credențiale admin | 🟡 |
| Job failure + refund automat | necesită job eșuat real | 🟡 |

---

## 8. Plan de remediere (ordine recomandată)

1. 🔴 **Rate limit pe `/api/auth/login`** — 1 h de lucru, blochează lansarea
2. 🟠 **Header-e de securitate + HSTS** — 30 min (nginx + Cloudflare)
3. 🟠 **`robots.txt` + `sitemap.xml` reale** — 30 min
4. 🟠 **Rută 404** în Vue Router — 30 min
5. 🟠 **`meta description` + Open Graph** — 30 min
6. 🟠 **WAF Skip pentru `/api/*`** în Cloudflare (monitorizare) — 15 min
7. 🟡 Reguli minore: `favicon.ico`, `404` pe API, `lang` dinamic, `www`
8. ⏳ **Test E2E complet** (register → generare → plată) înainte de trafic real

După 1–6, aplicația poate fi considerată **ready for launch** din perspectiva tehnică,
cu condiția finalizării testului E2E de la punctul 8.
