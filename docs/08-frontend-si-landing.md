# 08 – Frontend și pagina de start

Frontendul este o aplicație Vue 3 (Composition API) cu Pinia pentru stare și Vue Router.
Prima pagină este publică: arată o demonstrație „înainte/după” cu slider și permite
generarea unei imagini gratuite, fără cont.

## 1. Rute

| Rută | Acces | Conținut |
|---|---|---|
| `/` | public | landing: hero cu slider, generare gratuită, pași, ofertă |
| `/login` | public | autentificare / creare cont + butoane demo (dev) |
| `/studio` | autentificat | upload, listă job-uri, descărcare, retry |
| `/credits` | autentificat | abonamente RevenueCat, pachete de credite, istoric |
| `/admin` | `ADMIN` | toate job-urile, statistici, email, evenimente RevenueCat |

Garda din `src/router.js` redirecționează vizitatorii la `/login` cu `?redirect=...`
și blochează `/admin` pentru utilizatorii obișnuiți.

## 2. Componente

| Componentă | Rol |
|---|---|
| `LandingView.vue` | pagina de start: hero, slider demo, pași, ofertă |
| `BeforeAfterSlider.vue` | slider comparativ reutilizabil (pointer + tastatură) |
| `StylePicker.vue` | chips-uri cu miniatură pentru cele 26 de stiluri |
| `TrialGenerator.vue` | fluxul gratuit: email, fotografie, stil, rezultat + upsell |
| `JobUploader.vue` | upload pentru utilizatori autentificați |
| `JobList.vue` | job-urile utilizatorului, filtrare după status, descărcare, retry, ștergere |
| `CreditStore.vue` | sold, abonamente, pachete, istoric tranzacții |
| `AdminPanel.vue` | consola de operator |

## 3. Slider-ul „înainte / după”

`BeforeAfterSlider` primește două imagini (`before`, `after`) și o poziție inițială.
Implementarea:

- imaginea „after” este decupată cu `clip-path: inset(0 X% 0 0)`;
- un `<input type="range">` invizibil, suprapus peste tot containerul, oferă
  deplasare cu mouse/touch **și** accesibilitate din tastatură (săgeți);
- pointer events actualizează poziția în timp real, iar mânerul afișează procentul.

Pe pagina de start imaginile sunt `public/demo/before.jpg` (fotografie CC0) și
`public/demo/after.jpg` (derivare locală a aceleiași fotografii). Detalii despre
licență: `frontend/public/demo/CREDITS.md`.

După generare, aceeași componentă compară fotografia încărcată de vizitator cu
rezultatul primit de la server.

## 3b. Selectorul de stiluri

`StylePicker` afișează toate stilurile primite de la `GET /api/styles` ca o grilă de
butoane (role `radiogroup`), fiecare cu o miniatură de 24px și eticheta — în locul unui
dropdown cu 26 de intrări. Este folosit în `TrialGenerator` și `JobUploader`.

Pe pagina de start, secțiunea „Peste 26 de stiluri artistice” afișează **galeria
completă**: câte un card cu imaginea de exemplu a rezultatului pentru fiecare stil.
Click pe un card selectează stilul în formularul de generare și derulează la el.

Imaginile provin din `public/demo/styles/<style-id>.jpg` și sunt indicate de
`previewUrl` în răspunsul `/api/styles`. Sunt **generate cu Gemini** din fotografia
`public/demo/before.jpg`, prin `POST /api/admin/style-previews/regenerate` — nu sunt
filtre locale. Dacă o imagine lipsește, cardul afișează o placă cu numele stilului, iar
hero-ul arată doar fotografia (fără slider), ca să nu sugereze un rezultat care nu
există încă.

Fișierele scrise de server sunt redimensionate pentru web: cardurile din galerie la
`STYLE_PREVIEW_MAX_DIMENSION` (implicit 640px), iar imaginea din hero
(`public/demo/after.jpg`) la `STYLE_PREVIEW_HERO_DIMENSION` (implicit 1200px) și este
actualizată automat din exemplul stilului `anime_v1`.

Adăugarea unui stil în `application.yml` apare automat în UI după restart — nu e nevoie
de modificări în frontend.

## 4. Fluxul gratuit în UI

```text
email + fotografie + stil
        │
        ├─ POST /api/public/trial            → 202 { publicId, status: PENDING }
        ├─ GET  /api/public/jobs/{publicId}   → polling până la COMPLETED
        └─ GET  /api/public/jobs/{publicId}/download → blob pentru slider + descărcare
```

Stările afișate: `idle` → `uploading` → `processing` → `done` sau `error`.
La `TRIAL_ALREADY_USED` (402) formularul afișează îndrumarea către crearea unui cont,
iar butonul de generare este dezactivat după verificarea `GET /api/public/trial/eligibility`.

Starea este ținută în `src/stores/trial.js`, care eliberează `ObjectURL`-urile la
`reset()`/demontare ca să nu țină memoria ocupată.

## 5. Login demo (dezvoltare)

Când backendul rulează cu `app.demo.enabled=true` (profilul `dev`), `LoginView` cere
`GET /api/auth/demo-login` și afișează două butoane:

- **Cont demo** → utilizator obișnuit cu 25 de credite;
- **Operator demo** → cont cu rol `ADMIN`, pentru consola `/admin`.

Butonul apelează `POST /api/auth/demo-login` și primește un JWT normal, deci restul
aplicației funcționează identic cu un login real. În `prod` endpoint-ul nu există
(proprietatea este forțată pe `false`), iar UI-ul nu afișează butoanele.

## 6. Stiluri

Toate stilurile sunt în `src/assets/styles.css` (fără framework CSS): variabile CSS
pentru temă, grilă responsive, badge-uri de status, carduri și slider. Breakpoint-ul
principal este 900px (hero și formularele trec pe o coloană).
