# 13. Aplicații mobile — iOS și Android

Ultimele două faze ale proiectului: **Faza 1 = aplicația iOS**, **Faza 2 = aplicația
Android**. Ambele sunt construite din același cod frontend ca site-ul, nu ca rescriere.

Documentul descrie ce trebuie făcut, în ce ordine, ce se schimbă în cod și ce depinde
de conturi externe. Backendul rămâne aproape neschimbat: webhook-ul RevenueCat acordă
creditele la fel pentru web și pentru magazine.

## 1. Decizie de arhitectură: Capacitor, nu rescriere

| Opțiune | Efort | Observații |
|---|---|---|
| **Capacitor (aleasă)** | mic | Același `frontend/` (Vue 3) rulat într-un WebView nativ, cu plugin-uri pentru cameră și IAP. O singură bază de cod pentru ambele magazine. |
| React Native | mare | Fiecare ecran (`LandingView`, `CreditsView`, studio, `AccountView`) rescris. |
| Swift + Kotlin nativ | foarte mare | Două baze de cod separate. |
| PWA | mic | Fără listare în magazine, fără IAP — dar și fără descoperire. |

Motivul principal: aplicația este deja un SPA care vorbește cu backendul peste `/api`;
singurele lucruri care **nu** există în web și sunt obligatorii în magazine sunt
**camera/galerie** și **in-app purchase**. Restul (auth JWT, upload, joburi, credite,
ștergere cont) funcționează neschimbat.

## 1b. Stare implementare (2026-10-31)

| Pas | Stare | Dovadă |
|---|---|---|
| Capacitor 8.6.0 + 6 plugin-uri instalate | ✅ | `frontend/package.json` |
| `capacitor.config.json` | ✅ | `appId: xyz.cshub.toonmint`, `webDir: dist` |
| `src/lib/native.js` (cameră, IAP, entitlement, restore) | ✅ | modul nou |
| `VITE_API_BASE_URL` + avertisment la build nativ | ✅ | [`api/client.js`](../frontend/src/api/client.js) |
| Cameră în studio (nativ), upload prin fluxul existent | ✅ | [`JobUploader.vue`](../frontend/src/components/JobUploader.vue) |
| IAP + Restore în pagina de credite, regula abonamentului pe entitlement | ✅ | [`CreditStore.vue`](../frontend/src/components/CreditStore.vue), [`credits.js`](../frontend/src/stores/credits.js) |
| Proiect iOS generat (SPM, fără CocoaPods) | ✅ | `frontend/ios/`, `Package.swift` cu toate plugin-urile |
| Permisiuni cameră/fotografii în `Info.plist` | ✅ | `NSCameraUsageDescription`, `NSPhotoLibrary*` |
| Build iOS pentru simulator | ✅ | `xcodebuild … -sdk iphonesimulator` → **BUILD SUCCEEDED** |
| CORS pentru originile native pe producție | ✅ | `OPTIONS` → `Access-Control-Allow-Origin: capacitor://localhost` |
| Produse în App Store Connect / Play Console | ⛔ | necesită conturile de dezvoltator |
| Proiect Android | ⛔ | așteaptă Android Studio |

Ce lipsește ca aplicația să funcționeze cap-coadă: cheile publice RevenueCat
(`VITE_REVENUECAT_IOS_API_KEY`), produsele create în magazine și build-ul cu
`VITE_API_BASE_URL`. Fără ele aplicația pornește și se autentifică, dar cumpărările
eșuează cu „RevenueCat API key is not configured for this platform”.

## 2. Constrângerea care dictează totul: IAP

Apple (App Store Review Guidelines 3.1.1) și Google (Payments policy) cer ca
**bunurile digitale consumate în aplicație** — creditele și abonamentele — să fie
cumpărate prin **in-app purchase**, nu prin link extern.

Consecințe directe:

1. În aplicație **nu** se folosesc linkurile `https://pay.rev.cat/...` generate de
   [`BillingService.buildLink`](../backend/src/main/java/com/toonmint/service/BillingService.java).
   Se folosesc produsele din App Store / Play prin SDK-ul RevenueCat.
2. Regula introdusă pentru web — **pachetele de credite cer abonament activ**
   (`403 SUBSCRIPTION_REQUIRED`) — trebuie aplicată și în aplicație, dar *înainte* de
   `purchasePackage`, pentru că IAP nu trece prin `POST /api/credits/purchase`.
   Verificarea se face pe `CustomerInfo.entitlements.active['pro']`.
3. Produsele consumabile (pachetul de 20 de credite) trebuie **consumate** după
   cumpărare, altfel magazinul refuză recumpărarea. Cu setarea implicită a SDK-ului
   (`purchasesAreCompletedBy: REVENUECAT`) RevenueCat face el acknowledge/consume,
   deci aplicația nu are cod propriu pentru asta — important doar dacă se trece
   vreodată pe `MY_APP`.

Revendicarea creditelor rămâne neschimbată: RevenueCat trimite webhook-ul
`NON_RENEWING_PURCHASE` / `INITIAL_PURCHASE`, iar
[`RevenueCatService`](../backend/src/main/java/com/toonmint/service/RevenueCatService.java)
scrie în ledger. Idempotența pe `event_id` acoperă și dublurile web + mobil.

## 3. Pregătire comună (o singură dată, înainte de Faza 1)

### 3.1 Conturi și unelte

Capacitor **8.6.0** este versiunea curentă (nu 7): cere Node 22+, Xcode 26+ și
Android Studio 2025.2.1+. Cele două schimbări importante față de generația anterioară:
**SPM în loc de CocoaPods** pe iOS și **JDK-ul adus de Android Studio** (nu se instalează separat).

| Element | Necesar | Stare la 2026-10-31 |
|---|---|---|
| Apple Developer Program | cont plătit (99 USD/an) | de creat |
| App Store Connect | înregistrare aplicație, bundle id `xyz.cshub.toonmint` | după cont |
| Google Play Console | cont (25 USD o singură dată) | de creat |
| Xcode | ≥ 26.0 (Capacitor 8) | **27.2 instalat**, dar `xcode-select` arată încă `/Library/Developer/CommandLineTools` → o comandă de rulat (§3.1.1) |
| CocoaPods | **nu mai este necesar** — SPM e implicit; plugin-ul RevenueCat livrează `Package.swift` | — |
| Android Studio | ≥ 2025.2.1 | de instalat: **2026.2.2 „Rabbit 2”** (stabil), pachet macOS ARM64 |
| JDK pentru Gradle/AGP | cel inclus în Android Studio (JBR) | **nu** se folosește Java 27 de pe sistem |
| Node | ≥ 22 | 26.3 instalat |

### 3.1.1 Xcode: activează instalația

`/Applications/Xcode.app` există și funcționează (`DEVELOPER_DIR=... xcodebuild -version`
→ `Xcode 27.2`, build `27B52`), dar unealta activă este încă Command Line Tools, deci
`xcodebuild` fără `DEVELOPER_DIR` eșuează. O singură comandă (cere parola de admin):

```bash
sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer
xcode-select -p      # /Applications/Xcode.app/Contents/Developer
xcodebuild -version  # Xcode 27.2
```

Dacă `xcodebuild -runFirstLaunch` semnalează licența neacceptată:
`sudo xcodebuild -license accept`.

### 3.2 RevenueCat (proiectul existent `d6c976c5`)

1. Adaugă aplicația **App Store** (bundle id de mai sus) și **Google Play**
   (package `xyz.cshub.toonmint` — identic cu bundle id-ul iOS, deja setat în `capacitor.config.json`).
2. Produsele trebuie să aibă **exact** identificatorii din `application.yml`, altfel
   maparea produs → credite din [`AppProperties.RevenueCat`](../backend/src/main/java/com/toonmint/config/AppProperties.java) nu se aplică:

   | Product ID | Tip în magazin | Credite |
   |---|---|---|
   | `toonmint_weekly_499` | abonament, 7 zile | 50 |
   | `toonmint_yearly_2499` | abonament, 1 an | 300 |
   | `toonmint_credits_20` | **consumabil** | 20 |

3. Aceleași pachete în offering-ul `default`; entitlement-ul `pro` legat de cele două
   abonamente (deja există pentru web).
4. Cheile publice (SDK keys) pentru iOS și Android — intră în configul aplicației, nu
   în backend. Cheia secretă rămâne doar pe server.

### 3.3 Modificări de cod partajate

| # | Fișier | Schimbare |
|---|---|---|
| 1 | [`frontend/src/api/client.js`](../frontend/src/api/client.js) | `baseURL` din `import.meta.env.VITE_API_BASE_URL || '/api'` — în WebView `/api` este relativ la `capacitor://localhost`, deci nu ajunge la server. |
| 2 | `frontend/.env` (documentat în `.env.example`) | `VITE_API_BASE_URL=https://toonmint.cs-hub.xyz/api` pentru build-urile mobile. |
| 3 | `frontend/package.json` | `@capacitor/core@8.6.0`, `@capacitor/cli@8.6.0`, `@capacitor/ios@8.6.0`, `@capacitor/android@8.6.0`, `@capacitor/camera`, `@capacitor/preferences`, `@capacitor/app`, `@capacitor/status-bar`, `@capacitor/splash-screen`, `@revenuecat/purchases-capacitor@14`. Toate la majorul 8 — un plugin pe alt major rupe sync-ul. |
| 4 | `frontend/capacitor.config.json` (nou) | `webDir: "dist"`, `appId`, `appName: "ToonMint"`, `android.scheme: https`, `server.hostname: localhost`. |
| 5 | `frontend/src/lib/native.js` (nou) | `isNative()`, `takePhoto()` (cameră/galerie → base64), `purchasePackage()`, `restorePurchases()`, `hasEntitlement('pro')`. |
| 6 | [`frontend/src/components/JobUploader.vue`](../frontend/src/components/JobUploader.vue) | Pe mobil, `<input type="file">` (linia 125) se înlocuiește cu plugin-ul Camera; rezultatul base64 merge pe varianta JSON deja suportată de `POST /api/jobs` și `POST /api/public/trial` (`imageBase64`). |
| 7 | [`frontend/src/components/CreditStore.vue`](../frontend/src/components/CreditStore.vue) | `isNative()` ascunde butoanele care deschid `pay.rev.cat` și afișează cumpărarea prin IAP + **Restore purchases** (cerut de Apple). Păstrează modalul de abonament obligatoriu. |
| 8 | [`frontend/src/stores/credits.js`](../frontend/src/stores/credits.js) | Ramură nativă în `purchase()` / `openPlan()`: `purchasePackage` → apoi `startPaymentWatch(snapshot)` ca astăzi (creditele apar la webhook). |
| 9 | [`frontend/src/stores/auth.js`](../frontend/src/stores/auth.js) | **Nu se migrează.** `localStorage` persistă între lansări în WKWebView / Android WebView; mutarea în `@capacitor/preferences` ar cere inițializare asincronă în `state` și ar atinge toate fluxurile de auth, fără beneficiu dovedit. |
| 10 | `backend` env pe server | `CORS_ALLOWED_ORIGINS` += `capacitor://localhost`, `http://localhost`, `https://localhost` ([SecurityConfig](../backend/src/main/java/com/toonmint/config/SecurityConfig.java:88) respinge originile necunoscute). **Aplicat pe producție** și verificat cu `OPTIONS` din ambele origini. |

### 3.4 Sesiunea: azi expiră în 24 de ore

`app.jwt.ttl: PT24H` ([application.yml](../backend/src/main/resources/application.yml:52)) este
acceptabil pentru web (relogin rar), dar inacceptabil pentru o aplicație: utilizatorul
ar fi delogat zilnic. Înainte de Faza 1 se alege una dintre variante:

- **Recomandat:** `POST /api/auth/refresh` (token de refresh pe termen lung, stocat în
  Keychain/Keystore) — munca este în backend + `auth.js`.
- Minimal: TTL mai mare (ex. 90 de zile) doar pentru clienții mobili, identificați prin
  header — mai simplu, mai puțin sigur.

### 3.5 Rate limiting și IP-ul partajat

Limitele pe IP ([05-credite-si-rate-limiting.md](05-credite-si-rate-limiting.md)) se aplică
și în mobil, unde mulți utilizatori împart IP-ul operatorului. Pentru trial-ul gratuit
(1/adresă) cheia principală este emailul, deci riscul este mic; de urmărit la lansare.

## 4. Faza 1 — iOS

### 4.1 Pași

1. Activează Xcode 27.2 (§3.1.1). Nu se instalează CocoaPods — `npx cap add ios`
   folosește SPM.
2. `npm i` în `frontend/` (proiectul Capacitor există deja: `capacitor.config.json`,
   `appId: xyz.cshub.toonmint`).
3. `npm run build && npx cap add ios && npx cap sync ios`. Dacă vreun plugin nu expune
   `Package.swift`, refă adăugarea cu `--packagemanager CocoaPods` (necesită atunci
   `brew install cocoapods`).
4. `Info.plist`: `NSCameraUsageDescription`, `NSPhotoLibraryUsageDescription`,
   `NSPhotoLibraryAddUsageDescription` (texte în română și engleză — apar în UI-ul Apple).
5. `App.entitlements`: *In-App Purchase* (implicit), fără alte capabilități.
6. Montează plugin-urile din tabelul 3.3 și rulează pe simulator.
7. Creează produsele în App Store Connect (2 abonamente + 1 consumabil), completează
   prețurile și localizările; așteaptă starea *Ready to Submit*.
8. Test cu **Sandbox/StoreKit Testing** în Xcode: abonament → credite, pachet →
   respins fără abonament, pachet → 20 de credite cu abonament.
9. TestFlight intern, apoi trimitere la review.

### 4.2 Criterii de acceptare

- Autentificare, verificare OTP, resetare parolă, ștergere cont funcționează în aplicație.
- Poză din cameră și din galerie → job → previzualizare → descărcare în Poze.
- Trial gratuit o singură dată per email, cu mesajul `TRIAL_ALREADY_USED` tradus.
- Abonament cumpărat din aplicație → `subscriptionActive` devine true, creditele din plan apar.
- Pachet fără abonament → modal „Pachetele de credite necesită abonament”, fără fereastră de plată.
- Pachet cu abonament → cumpărare consumabilă, +20 credite, recumpărabil imediat.
- **Restore purchases** disponibil în `CreditStore.vue`.
- Nicio referință la `pay.rev.cat` sau la prețuri externe în bundle (verificabil cu `grep`).

### 4.3 Checklist de trimitere

- Privacy Policy URL public (`/legal/confidentialitate`) + suport (email de contact).
- Ștergerea contului accesibilă în aplicație (există în [AccountView](../frontend/src/views/AccountView.vue:52), Apple 5.1.1(v)).
- Screenshot-uri 6.9", 6.5", 5.5" + iPad dacă se permite instalarea pe iPad.
- Notă de review: aplicația generează imagini AI din fotografii; contul de test cu
  credite pus la dispoziția reviewer-ului.
- Rating: 12+ (imagini generate de AI); fără conținut pentru copii.
- Export compliance: doar HTTPS standard → „ITSAppUsesNonExemptEncryption = NO”.

## 5. Faza 2 — Android

Reutilizează integral Faza 1; diferă doar ce ține de platformă.

### 5.1 Pași

1. Android Studio + SDK 35 + **JDK 21** (Java 27 de pe mașină nu este suportat de AGP).
2. `npm run build && npx cap add android && npx cap sync android`.
3. `AndroidManifest.xml`: `CAMERA`, `READ_MEDIA_IMAGES` (API 33+), `INTERNET`.
4. Configurare Play Console: aplicație, package `xyz.cshub.toonmint`, produse identice,
   apoi upload AAB în *Internal testing*.
5. Test pe dispozitiv real (camera emulatorului nu acoperă EXIF/HEIC).
6. Verifică descărcarea imaginii în galerie prin `@capacitor/filesystem` (pe Android
   `Download` nu este accesibil direct din WebView).

### 5.2 Criterii de acceptare

Aceleași ca la 4.2, plus: helm Android, back button nu închide aplicația din greșeală,
rotirea ecranului nu pierde jobul în curs, notificarea de expirare a imaginii respectată.

## 6. Riscuri

| Risc | Impact | Atenuare |
|---|---|---|
| Respins la review pentru link de plată extern | blocant | Modul nativ ascunde complet linkurile `pay.rev.cat`; `grep` în bundle înainte de upload. |
| Consumabil necumpărabil a doua oară | funcțional | `consumePurchase` după confirmarea webhook-ului. |
| Produse cu alt ID în magazin | creditele nu se acordă | Verificare înainte de lansare: ID identic cu `application.yml`. |
| Logout zilnic (JWT 24h) | UX | Refresh token (§3.4). |
| Upload din cameră eșuează (HEIC) | funcțional | Camera plugin livrează JPEG/base64; backend acceptă PNG/JPEG/WEBP — de validat `.heic` respins corect cu `BAD_REQUEST`. |
| `xcode-select` arată încă Command Line Tools | build iOS eșuează cu „requires Xcode” | `sudo xcode-select --switch /Applications/Xcode.app/Contents/Developer` (§3.1.1). |
| Android Studio folosește Java 27 de pe sistem | AGP refuză JDK-ul | În Android Studio: *Settings → Build → Build Tools → Gradle → Gradle JDK* = **Embedded JDK (JBR)**; nu se adaugă Java 27 ca SDK. |

## 7. Estimare

| Fază | Dezvoltare | Review magazin | Total realist |
|---|---|---|---|
| Pregătire comună (§3) | 1–2 zile | — | 1–2 zile |
| Faza 1 iOS | 4–6 zile | 1–3 zile | 1–2 săptămâni |
| Faza 2 Android | 2–3 zile | 1–7 zile (cont nou) | 1–2 săptămâni |

## 8. Decizii deschise

1. Bundle id / package: `xyz.cshub.toonmint` pentru ambele platforme (deja în config) — se confirmă înainte de înregistrarea în magazine.
2. Refresh token (§3.4) sau TTL lung.
3. Dacă pachetele de credite rămân disponibile în aplicație sau doar în web (Apple
   permite ambele, dar top-up-urile în aplicație cresc complexitatea review-ului).
4. Dacă se dorește și iPad/tabletă (afectează screenshot-urile și layout-ul).
