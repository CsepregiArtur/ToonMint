# 12 – Verificare de lansare (audit live pe server)

**Data:** 2026-10-31
**Mediu:** producție — `https://toonmint.cs-hub.xyz`
**Versiune testată:** `1.0.0` (commit `fe9fc34`, tag `v1.0.0`)
**Tip audit:** read-only (fără creare de conturi, fără plăți, fără generări AI)

---

## Verdict

> **Audit inițial: NU era „ready for launch".**
> Un blocker de securitate + 5 probleme majore.
>
> **După remediere (secțiunea 9):** toate problemele de **cod** sunt rezolvate și testate.
>
> **Re-testare live (secțiunea 10):** frontend-ul nou e **deployed și verificat**.
> ⚠️ **Backend-ul NU e redeployat** — rate limiting-ul pe login nu e activ în producție.
> Rămân **4 acțiuni** în afara repo-ului: redeploy backend, header-e nginx,
> HSTS/Cloudflare, WAF skip.

| Categorie | Inițial | După remediere | La re-testare (live) |
|---|---|---|---|
| 🔴 Blocker | 1 | 0 (în cod) | **1 — backend nedeplyat** |
| 🟠 Major | 5 | 2 (pe server) | 3 (+ WAF pe sitemap) |
| 🟡 Minor | 6 | 4 | 4 |
| ⏳ Netestate (necesită cont/plată) | 6 | 6 | 6 |

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
| `<meta name="description">` | există în `index.html` (RO) | ✅ |
| Open Graph (`og:title`, `og:image`, `og:description`) | **lipseau** → adăugate | ✅ (după fix) |
| `robots.txt` | returna **HTML** (index.html) → fișier real adăugat | ✅ (după fix) |
| `sitemap.xml` | returna **HTML** (index.html) → fișier real adăugat | ✅ (după fix) |
| Rută 404 (SPA) | **nu exista** — URL necunoscut → redirect la `/login` | ✅ (după fix) |
| `lang="ro"` în `index.html` | `setLocale()` actualizează `<html lang>` la runtime | ✅ |

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

### 🟠 M5. Lipsesc meta Open Graph

`index.html` avea `description` (RO), dar **niciun tag `og:*` / `twitter:*`**. Fără ele,
share-urile pe social/WhatsApp apar fără imagine și fără titlu, iar Google afișează un
snippet generat arbitrar.

**Fix aplicat:** `og:type`, `og:site_name`, `og:url`, `og:title`, `og:description`,
`og:image` (1200×630 generat), `og:image:width/height/alt`, `og:locale`, `twitter:card`,
plus `canonical` și `apple-touch-icon` 180×180.

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

---

## 9. Remediere aplicată (2026-10-31)

### ✅ Rezolvate în cod

| # | Problemă | Fix | Verificare |
|---|---|---|---|
| B1 | Login fără rate limiting | `AuthService.login(request, clientIp)` apelează `rateLimitService.check("login-ip:…")` și `check("login-email:…")`; `AuthController` trimite `ClientIp` | `LoginRateLimitTest` (limit=2 → al 3-lea login = 429) |
| M3 | `robots.txt` / `sitemap.xml` inexistente | `frontend/public/robots.txt` + `sitemap.xml` (5 URL-uri publice) | prezente în `dist/` |
| M4 | Fără pagină 404 | `NotFoundView.vue` + rută catch-all `/:pathMatch(.*)*` cu `meta.public` | build OK |
| M5 | Fără Open Graph | `og:*` + `twitter:*` + `canonical` + `og-image.png` 1200×630 în `index.html` | prezente în `dist/index.html` |
| m-favicon | `/favicon.ico` returna HTML | `frontend/public/favicon.ico` real (16/32/48 px) + `apple-touch-icon.png` 180 px | fișiere în `dist/` |

**Teste:** `mvn test` → **67 teste, 0 eșecuri** (era 66). Frontend: `vite build` OK.

### ⚠️ Rămân de făcut pe server (în afara repo-ului)

| # | Problemă | Unde | Cum |
|---|---|---|---|
| M1 | Header-e de securitate pe HTML | `nginx` | vezi snippet mai jos |
| M2 | HSTS | Cloudflare | SSL/TLS → Edge Certificates → **Enable HSTS** (6 luni) |
| — | Blocaj bot pe `/api/*` | Cloudflare | WAF → Skip pe `starts_with(http.request.uri.path, "/api/")` |

Snippet `nginx` pentru `location /` (partea de SPA):

```nginx
location / {
    try_files $uri $uri/ /index.html;

    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header Referrer-Policy "strict-origin-when-cross-origin" always;
    add_header Permissions-Policy "camera=(), microphone=(), geolocation=()" always;
    # Pornește în Report-Only o săptămână, apoi scoate -Report-Only.
    add_header Content-Security-Policy-Report-Only "default-src 'self'; img-src 'self' data: blob: https:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'" always;
}
```

### 🟡 Acceptate conștient (nu blochează lansarea)

| Problemă | Motivație |
|---|---|
| `/api/nonexistent` → `401` | `anyRequest().authenticated()` filtrează înainte de rutare; a returna 404 ar cere restructurarea lanțului de securitate, fără beneficiu de securitate |
| `/api/public/trial/eligibility` permite enumerare email | impact minim (răspuns binar), cost de schimbare a contractului API |
| `www.` nu rezolvă | domeniul canonic e fără `www`, consecvent în tot site-ul |
| GTM/GA4/AdSense neconfigurate | intenționat: `VITE_*` sunt goale până la lansare |

### 🔴 Rămâne obligatoriu înainte de trafic real

**Testul E2E complet**, care nu poate fi automatizat fără cost și cont real:
`register → cod email → login → upload poză → generare → download → cumpărare card 4242 → credite în DB`.

---

## 10. Re-testare live (2026-10-31, după deploy)

Re-verificare completă pe `https://toonmint.cs-hub.xyz`, la ~20 minute după commit-ul `4d07e0a`.

### 10.1 Rezultate — ce s-a rezolvat

| Verificare | Înainte | Acum | Dovadă |
|---|---|---|---|
| `robots.txt` | `200 text/html` (index.html) | **`200 text/plain`** | primul rând: `User-agent: *` |
| `sitemap.xml` | `200 text/html` (index.html) | **`200 text/xml`**, 5 URL-uri | `<?xml version="1.0"…` + 5× `<loc>` |
| `favicon.ico` | `200 text/html` | **`200 image/x-icon`**, 5 318 B | header ICO valid (16/32/48 px) |
| `apple-touch-icon.png` | lipsea (SVG, nesuportat de iOS) | **`200 image/png`** 180×180 | `rel="apple-touch-icon"` în HTML |
| `og-image.png` | 404 | **`200 image/png`** 1200×630 | assets servite corect |
| Meta Open Graph | 0 tag-uri | **9 `og:*` + 4 `twitter:*` + `canonical`** | `og:image` = `https://toonmint.cs-hub.xyz/og-image.png` |
| Pagina 404 | URL necunoscut → redirect la `/login` | **pagina 404**, URL păstrat | `/this-page-does-not-exist-xyz` → „404 / This page does not exist", butoane „Back to home" + „Go back" |
| Landing (regresie) | — | fără erori JS, fără request-uri eșuate | 0 failed, 0 pageerror |
| Conținut dinamic | 26 stiluri, 3 prețuri | **26 stiluri, $4.99 / $24.99 / $1.99** | `/api/styles` + `/api/public/billing/catalog` = 200 |
| Performanță | DCL 99 ms / load 130 ms | **DCL 116 ms / load 153 ms** | în limite normale |

> **Notă:** la prima încărcare în browser, `robots.txt` și `favicon.ico` au fost servite din
> **cache-ul HTTP local** (răspunsurile vechi de tip HTML, memorate din auditul anterior).
> Cu cache-busting (`?cb=…`) fișierele reale sunt servite corect. Nu e o problemă de server,
> dar un utilizator cu cache vechi trebuie să dea hard-reload o singură dată.

### 10.2 🔴 Regresie critică: backend-ul NU este redeployat

Rate limiting-ul pe login **nu este activ în producție**.

| Test | Rezultat |
|---|---|
| 12 login-uri eșuate consecutive | `[401 × 12]` — niciun `429` |
| Încă 20 (total **32**) | `[401 × 32]` — niciun `429`, fără `Retry-After` |

Limita configurată e **10/minut** (`RATE_LIMIT_REQUESTS_PER_MINUTE:10`), deci al 11-lea ar fi trebuit
respins. Concluzie: **jar-ul backend din producție este versiunea veche** — codul e în `main`
(commit `4d07e0a`), dar nu a fost construit și repornit.

**Acțiune obligatorie:**

```bash
cd backend && mvn -DskipTests package     # → target/toonmint-backend-1.0.0.jar
# oprește serviciul, înlocuiește jar-ul, repornește
```

Atenție: după bump-ul de versiune, artefactul se numește **`toonmint-backend-1.0.0.jar`**
(nu `toonmint-backend-0.1.0-SNAPSHOT.jar`) — actualizează scriptul de deploy / `systemd` / Dockerfile
dacă numele e hardcodat. Local în `backend/target/` există încă `toonmint-backend-0.1.0-SNAPSHOT.jar`,
semn că backend-ul nu a fost reconstruit după `fe9fc34`.

**Verificare după redeploy:** 11 login-uri eșuate → al 11-lea răspunde `429 RATE_LIMIT_EXCEEDED` + `Retry-After`.

### 10.3 ⚠️ Rămân neschimbate (în afara repo-ului)

| # | Problemă | Dovadă la re-testare |
|---|---|---|
| M1 | Header-e de securitate pe HTML | `GET /` → `strict-transport-security`, `content-security-policy`, `x-frame-options`, `x-content-type-options`, `referrer-policy`, `permissions-policy` = **toate absente** |
| M2 | HSTS | absent pe HTML și pe API |
| — | WAF skip pe `/api/*` | `curl` → `403 "Your request was blocked."` pe `/api/styles`; webhook-ul rămâne `401` (excepția lui funcționează) |
| — | **Nou: `sitemap.xml` blocat pentru clienți non-browser** | `curl` (chiar și cu UA de Chrome) → `403`; din browser → `200 text/xml` |

**De ce contează `sitemap.xml`:** `robots.txt` este permise explicit de Cloudflare, dar `sitemap.xml`
nu. Dacă botul de crawling nu e pe lista „verified bots", XML-ul e respins și sitemap-ul e ignorat.
Adaugă în regula WAF de skip și `/sitemap.xml` (sau permite `starts_with(http.request.uri.path, "/sitemap")`).

### 10.4 Verdict după re-testare

| Zonă | Stare |
|---|---|
| Frontend (build nou) | ✅ deployat și verificat |
| SEO tehnic (robots, sitemap, OG, favicon, 404) | ✅ rezolvat |
| Securitate backend (rate limit login) | ⛔ **cod gata, dar nedeplyat** |
| Header-e de securitate + HSTS | ⛔ neschimbate (nginx / Cloudflare) |
| Cloudflare bot protection pe `/api/*` și `/sitemap.xml` | ⛔ neschimbat |
| Test E2E complet | ⏳ nerealizat |

> **Concluzie:** aplicația **nu este încă production-ready** — mai sunt **4 acțiuni de infrastructură**
> (redeploy backend, header-e nginx, HSTS, WAF skip) plus testul E2E.
> Din punct de vedere al codului, totul e implementat și testat (67 teste, 0 eșecuri).
