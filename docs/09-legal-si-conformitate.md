# 09 – Legal, GDPR și consimțământ

Ce trebuie să existe înainte de lansarea publică și ce lipsește încă. Documentul
urmărește legislația aplicabilă unui serviciu online cu utilizatori din UE/România:
**GDPR**, **Legea 190/2018**, **ePrivacy / Legea 506/2004** (cookie-uri),
**Legea 365/2002** (comerț electronic), **OUG 34/2014** (drepturile consumatorilor),
**Legea 82/1991** (contabilitate), **Regulamentul (UE) 2024/1689** (AI Act, art. 50 —
transparența conținutului generat).

> ⚠️ Textele de pe site sunt un punct de plecare corect din punct de vedere
> structural, **nu consultanță juridică**. Înainte de lansare cere o verificare
> unui jurist și completează toate `TODO`-urile din `frontend/src/legal/site.js`.

---

## 1. Ce este implementat

| Piesă | Fișier | Note |
|---|---|---|
| Politici legale — **română și engleză** | `frontend/src/legal/documents.js`, `documents.en.js` | 4 documente; EN se afișează pentru orice limbă ≠ `ro`, versiunea RO prevalează |
| Date operator + versiune documente | `frontend/src/legal/site.js` | **sursa unică**; conține `TODO`-urile de completat |
| Pagini legale accesibile fără cont | `frontend/src/views/LegalView.vue`, `router.js` | `/legal/confidentialitate`, `/legal/cookies`, `/legal/termeni`, `/legal/licente` |
| Banner de consimțământ + panou granular | `frontend/src/components/CookieNotice.vue` | „Accept toate” / „Doar esențiale” / „Personalizează” |
| Stocarea deciziei (versiune, dată, categorii) | `frontend/src/stores/consent.js` | `localStorage: toonmint.consent.v1`, reînnoire la 6 luni |
| Etichete încărcate **doar** după acord | `frontend/src/lib/analytics.js` | GTM, GA4, AdSense, Clarity; ID-uri din `VITE_*` |
| Consent Mode v2 implicit „denied” | `main.js` → `bootstrapConsentDefaults()` | `ad_storage`, `ad_user_data`, `ad_personalization`, `analytics_storage` |
| Ștergerea cookie-urilor la retragere | `analytics.js` → `clearTrackingCookies()` | best-effort pe domeniul propriu |
| Bifă obligatorie la creare cont | `views/LoginView.vue` | termeni + confidențialitate + „am 16 ani” |
| Informare la generarea gratuită | `components/TrialGenerator.vue` | drepturi asupra imaginii, minori |
| Footer juridic + ANPC/SAL + SOL | `App.vue` | linkuri permanente, cerute de Legea 365/2002 și ANPC |
| Headere de securitate + CSP | `backend/.../security/SecurityHeadersFilter.java` | CSP include gazdele Google/Clarity |
| Variabile pentru ID-urile de tracking | `frontend/.env.example` | goale = eticheta nu se încarcă |

---

## 2. Ce lipsește (lista de lucru)

### P0 — obligatoriu înainte de lansare

| # | Ce lipsește | Temei | Unde se rezolvă |
|---|---|---|---|
| 1 | ~~Datele reale ale firmei~~ — completat: **Csepregi Artur**, Satu Mare, `contact@cs-hub.xyz`, `+40 750 418 155`, `https://toonmint.cs-hub.xyz` | Legea 365/2002 art. 5; ANPC | `frontend/src/legal/site.js` ✅ |
| 1b | **CUI + nr. ONRC** — doar dacă vinzi prin PFA/SRL (altfel identificarea ca persoană fizică e suficientă) | Legea 365/2002 | `site.js` → `entity.cui` / `entity.regCom` (apar automat în pagini când sunt completate) |
| 2 | ~~Dreptul de ștergere a contului~~ — implementat: `DELETE /api/users/me` + pagina **Cont** din UI (anonimizare, joburi și coduri șterse, dovadă fiscală păstrată) | GDPR art. 17 | ✅ `AccountService`, `AccountView.vue` |
| 3 | ~~Exportul datelor~~ — implementat: `GET /api/users/me/export` + buton „Descarcă (JSON)” | GDPR art. 20 | ✅ `AccountService` |
| 4 | ~~Dovada consimțământului~~ — implementat: `users.terms_version` + `terms_accepted_at`, trimise la înregistrare; apar în export | GDPR art. 7(1) | ✅ migrare `V5__legal_consent.sql` |
| 5 | **Registru de prelucrări (ROPA)** — chiar și un tabel simplu în `docs/` | GDPR art. 30 | document nou `docs/10-registru-prelucrari.md` |
| 6 | **Politica de retenție aplicată tehnic**: ștergerea metadatelor job după N luni, curățarea `email_messages` | GDPR art. 5(1)(e) | job programat (`@Scheduled`) + migrare |
| 7 | **Procedura de breach** (cine anunță, în 72 h, template ANSPDCP) | GDPR art. 33–34 | `docs/07-securitate.md`, secțiune nouă |
| 8 | **Verificarea numelui/mărcii** „ToonMint” (OSIM / EUIPO) și rezervarea domeniului | Legea 84/1998 | acțiune externă |

### P1 — înainte de a activa tracking / AdSense

| # | Ce lipsește | Temei | Note |
|---|---|---|---|
| 9 | **CMP certificat Google** pentru AdSense în SEE | Politica Google (din ian. 2024) | bannerul propriu **nu** este certificat. Pentru anunțuri personalizate e nevoie de un CMP certificat (Funding Choices din AdSense este gratuit) sau de un furnizor certificat. Până atunci: doar anunțuri nepersonalizate sau deloc |
| 10 | **`ads.txt`** la rădăcina domeniului | specificație IAB / AdSense | `google.com, pub-XXXXXXXX, DIRECT, f08c47fec0942fa0` — se creează după ce ai publisher ID-ul |
| 11 | **Meta de verificare AdSense** (`google-adsense-account`) | AdSense | se adaugă în `index.html` după ce ai ID-ul |
| 12 | **`robots.txt` + `sitemap.xml`** și politică de crawler AI | bune practici | opțional, dar util la lansare |
| 13 | **Verificarea accesului Google** prin Cloudflare (Bot Fight Mode / AI Crawl Control) | AdSense / Search Console | fără skip rule pe ASN 15169/19527/396982, Google raportează „tag missing” |
| 14 | **Lista completă de subprocesatori** cu locații și garanții de transfer | GDPR art. 28, 44+ | de completat în politica de confidențialitate (secțiunea 5) după ce alegi găzduirea |

### P2 — în termen scurt după lansare

| # | Ce lipsește | Temei |
|---|---|---|
| 15 | **DPIA** pentru prelucrarea imaginilor de persoane cu AI | GDPR art. 35 (recomandat puternic pentru biometrie accidentală / imagini de persoane) |
| 16 | **Procedura DSAR** (model de răspuns în 30 de zile, identitate verificată) | GDPR art. 12 |
| 17 | **Contracte de prelucrare (DPA)** semnate cu Google (API Gemini), RevenueCat, găzduire | GDPR art. 28 |
| 18 | **Politica de cookie-uri completată** cu duratele reale după ce tag-urile sunt active | ePrivacy |
| 19 | **Consimțământ pentru marketing prin email** (double opt-in + dezabonare) — doar dacă apare newsletter | Legea 506/2004 art. 6, GDPR art. 7 |
| 20 | **Notificare de modificare a termenilor** (email + anunț în app, 15 zile) | OUG 34/2014 |
| 21 | ~~Confirmarea expresă la checkout pentru pierderea dreptului de retragere~~ — implementat (bifă obligatorie + `POST /api/billing/acknowledgements`) | OUG 34/2014 art. 16 lit. m) | ✅ |
| 22 | **Facturare**: datele firmei pe factură, serii/numerotare, arhivare 10 ani | Legea 82/1991, Codul fiscal |
| 23 | **Accesibilitate** (EAA, din 28 iunie 2025) — contrast, focus, ARIA | Directiva (UE) 2019/882 |
| 24 | **`security.txt`** / canal de raportare a vulnerabilităților | bune practici (art. 32) |

---

## 3. Licențiere — ce trebuie acoperit

| Aspect | Stare |
|---|---|
| Cod propriu (backend + frontend) | proprietar; `README` poate primi un `LICENSE` explicit (ex. „All rights reserved”) |
| Dependențe Maven/npm | listate în `/legal/licente`; pentru conformitate completă rulează `mvn license:aggregate-add-third-party` și `npx license-checker --summary` |
| Imagini demo | `frontend/public/demo/CREDITS.md` — CC0 + generate cu AI ✅ |
| Fonturi | se folosesc fonturi de sistem / `Inter` local; **nu** încărca Google Fonts extern (ar fi cerere către terț, cu implicații GDPR + CSP) |
| Nume de stiluri | descriptive (anime, manga, pop art) — fără referiri la artiști ✅ |
| Conținut încărcat de utilizator | licență tehnică limitată, definită în termeni §7 ✅ |
| Rezultate generate | drept de utilizare comercială acordat utilizatorului, cu obligația de marcare AI ✅ |
| Termeni Google Gemini API | de verificat că proiectul folosește **tier-ul plătit** (fără antrenare pe datele trimise) și respectă Generative AI Prohibited Use Policy |

---

## 4. Cum funcționează consimțământul în cod

```text
main.js
  └─ bootstrapConsentDefaults()      → dataLayer + Consent Mode „denied”
App.vue
  └─ <CookieNotice />
        └─ consent.init()            → citește localStorage; dacă e valid, aplică și încarcă tag-urile
              ├─ user apasă „Accept toate”      → persist({analytics:true, ads:true})
              ├─ user apasă „Doar esențiale”    → persist({analytics:false, ads:false}) + șterge cookie-uri
              └─ „Personalizează”               → panou cu două comutatoare → persist(draft)
```

Reguli respectate:

1. **Niciun script terț în `index.html`** — doar `analytics.js` le injectează, după acord.
2. **Refuzul e la fel de simplu** ca acceptul (buton de același nivel).
3. **Consimțământul expiră** după 6 luni sau când se schimbă `SITE.docVersion`.
4. **Retragerea** setează `denied` în Consent Mode și șterge cookie-urile first-party cunoscute.
5. **Fără ID configurat** (dev, staging), nicio etichetă nu se încarcă.

---

## 5. Punerea în producție

### Frontend

```bash
cp frontend/.env.example frontend/.env.local   # completează VITE_* după ce ai conturile
cd frontend && npm run build                   # ID-urile sunt „coapte” în bundle
```

> ID-urile `VITE_*` sunt **publice** (apar în HTML/JS) — nu pune secrete acolo.
> Schimbarea lor cere rebuild, nu doar restart.

> **Domeniul de producție** `https://toonmint.cs-hub.xyz` trebuie adăugat în `CORS_ALLOWED_ORIGINS`,
> altfel browserul blochează apelurile către API. El intră automat și în CSP (img-src / connect-src),
> pentru că politica este construită din aceeași listă.

### Limba documentelor

Textele legale există în **română** și **engleză**. Pentru orice limbă din selector diferită de
română se afișează versiunea engleză, cu mențiunea „In case of discrepancy, the Romanian version
prevails” (echivalentul românesc al aceleiași clauze în `legal.notice`).

Dacă traduci documentele într-o altă limbă, adaugă un fișier `documents.<cod>.js` și extinde
`getDocument()` cu limba respectivă — structura de blocuri (`p`, `ul`, `table`, `note`) și ancorele
de secțiune trebuie păstrate identice.

### Cum schimbi versiunea documentelor

Versiunea are **o singură sursă**: `LEGAL_TERMS_VERSION` din `.env` (rădăcina repo-ului).

1. Modifici textele în `documents.js` / `documents.en.js`.
2. Schimbi `LEGAL_TERMS_VERSION=2027-01-15` în `.env` (și în `.env.example`, ca să rămână documentat).
3. Repornești backendul (validează versiunea la înregistrare și la checkout) **și**
   rulezi `npm run build` în frontend (valoarea e injectată la build de `vite.config.js`).

Dacă un client rulează un bundle vechi, primește `409 TERMS_VERSION_OUTDATED` cu
`details.currentVersion` — semn clar că trebuie reîncărcat pagina, nu o acceptare tăcută a
unei versiuni care nu mai e publicată.

### Backend

Headerele (inclusiv CSP) sunt adăugate de `SecurityHeadersFilter`. Dacă în față stă
nginx, headerul ajunge deja de la aplicație — nu îl duplica. Dacă UI-ul static este
servit de nginx fără backend, adaugă acolo:

```nginx
add_header Content-Security-Policy "<politica din SecurityHeadersFilter>" always;
add_header X-Content-Type-Options nosniff always;
add_header Referrer-Policy strict-origin-when-cross-origin always;
```

`always` este obligatoriu: altfel nginx nu trimite headerul pe răspunsurile 4xx/5xx.

### Verificare după deploy

| Verificare | Cum |
|---|---|
| Bannerul apare la prima vizită și dispare după alegere | fereastră incognito |
| Fără acord, nicio cerere către `googletagmanager.com` | DevTools → Network, filtru `gtm` |
| Cu acord, tag-urile se încarcă | Network + GA4 → Realtime |
| CSP nu blochează nimic | Console: niciun `Refused to load/execute` |
| `ads.txt` răspunde 200 | `curl -sI https://domeniu/ads.txt` |
| Google poate accesa site-ul | `curl -sI -H "User-Agent: Mozilla/5.0 ..." https://domeniu/` → nu 403 |

---

## 6. Forma de organizare — care e cea mai sigură juridic

Siguranța juridică nu înseamnă optim fiscal. Înseamnă trei lucruri, în ordinea importanței:

1. **răspundere limitată** (nu răspunzi cu patrimoniul personal);
2. **entitate în țara în care lucrezi efectiv** (fără sediu permanent / rezidență dublă);
3. **să poți onora ce scrie în politici** — o promisiune neîndeplinită (ex. „îți ștergem
   contul la cerere”) e mai periculoasă decât forma de organizare.

| Setup | Siguranță | De ce |
|---|---|---|
| **SRL (RO) + merchant of record / App Store** | 🟢 cea mai sigură | răspundere limitată, entitate acolo unde lucrezi, TVA-ul și obligațiile de consumator preluate de vânzătorul de record |
| **PFA (RO) + MoR + asigurare RC profesională** | 🟡 acceptabilă | simplu și ieftin; răspunderea rămâne personală, dar o poliță de răspundere civilă profesională (~300–600 lei/an) acoperă daunele tipice |
| **PFA (RO) fără asigurare** | 🟠 risc moderat | orice litigiu (imagine, drepturi de autor, date) se acoperă din patrimoniul personal |
| **Firmă în HU cu rezidență în RO** | 🔴 cea mai riscantă | locul conducerii efective → dublă rezidență posibilă, sediu permanent, două administrații fiscale |
| **Persoană fizică neînregistrată** | ⛔ ilegal | OUG 44/2008 art. 3 — activitatea obișnuită cere formă de organizare |

Recomandare: **SRL micro în România + vânzare prin MoR sau magazine de aplicații**. Dacă
bugetul de start nu permite, PFA + MoR + asigurare RC profesională este varianta minimă
acceptabilă.

## 7. Riscurile reale ale acestui produs (dincolo de taxe)

| Risc | De ce e specific ToonMint | Măsură |
|---|---|---|
| Fotografii cu **minori** sau cu **terți** fără consimțământ | upload liber, fără moderare | interdicție în termeni ✅, verificare la upload, canal de takedown cu răspuns în 24–48 h |
| **Deepfake / imaginea unor persoane reale** | generare de portrete convingătoare | marcare „creat cu AI” ✅, interdicție de context înșelător ✅, jurnal de joburi pentru apărare |
| **Drepturi de autor pe stiluri** | imitarea unui artist | nume descriptive (anime, manga), fără nume de artiști ✅ |
| **GDPR** — ștergere, acces, retenție | promisiuni în politică | ștergere + export implementate ✅, consimțământ în DB ✅, retenție automată (încă în backlog) |
| **Consumatori** — retragere, preț cu TVA | credite digitale livrate instant | confirmare la checkout implementată ✅, prețul total urmează |

## 8. Backlog tehnic creat de cerințele legale

- [x] `DELETE /api/users/me` — ștergere cont + date asociate (P0 #2)
- [x] `GET /api/users/me/export` — export JSON (P0 #3)
- [x] `users.terms_version` / `terms_accepted_at` + trimitere din frontend la înregistrare (P0 #4)
- [x] Confirmare la checkout: „solicit livrarea imediată și accept pierderea dreptului de retragere” (P2 #21) — `POST /api/billing/acknowledgements` + bifă în pagina de credite
- [ ] `POST /api/consent` — jurnal de consimțământ pe server (opțional, dar e cea mai bună dovadă)
- [ ] Curățare programată: joburi mai vechi de N luni, `email_messages` > 30 zile (P0 #6)
- [ ] Pagina `/legal/*` în sitemap, cu `lastmod` din `SITE.docVersion`
