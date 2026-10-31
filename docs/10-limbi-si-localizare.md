# 10 – Limbi și localizare

Site-ul și aplicația se adaptează la limba vizitatorului: 20 de limbi, detectare
automată după locul de unde se deschide pagina și selector manual care rămâne salvat.

## 1. Limbile disponibile

| Cod | Limba | Cod | Limba |
|---|---|---|---|
| `en` | English | `bg` | Български |
| `ro` | Română | `hr` | Hrvatski |
| `fr` | Français | `el` | Ελληνικά |
| `de` | Deutsch | `sv` | Svenska |
| `es` | Español | `da` | Dansk |
| `it` | Italiano | `fi` | Suomi |
| `pt` | Português | `nb` | Norsk bokmål |
| `nl` | Nederlands | `tr` | Türkçe |
| `pl` | Polski | | |
| `cs` | Čeština | | |
| `sk` | Slovenčina | | |
| `hu` | Magyar | | |

Lista este definită într-un singur loc: `frontend/src/i18n/index.js` → `LOCALES`.
Selectorul din antet și din subsol se generează automat din ea.

## 2. Cum se alege limba

Ordinea de detecție (`detectLocale()`), prima potrivire câștigă:

1. **`?lang=xx`** din URL — link partajabil: `https://domeniu/?lang=de`.
   Se salvează imediat ca preferință.
2. **Preferința salvată** — `localStorage['toonmint.locale']`.
3. **Limba browserului** — `navigator.languages`, în ordine (ex. `pl-PL` → `pl`).
4. **Fusul orar** — tabel `TIMEZONE_LOCALE` (ex. `Europe/Warsaw` → `pl`).
5. **`en`** — dacă nimic nu se potrivește.

Detecția este **locală**: nu se face nicio cerere de geo-IP, deci alegerea limbii
nu are nevoie de consimțământ și nu trimite adresa IP nimănui. Fusul orar este o
aproximare de țară — un vizitator poate schimba limba oricând din selector.

Elementele care se adaptează automat:

- toate textele interfeței, prin `t('cheie')`;
- `<html lang="…">`, pentru cititoare de ecran și pentru motoarele de căutare;
- datele calendaristice (`formatDate`, `toLocaleString(locale)`) — ex. „31 octombrie 2026” vs „31 October 2026”;
- numerele și pluralul (`Intl.PluralRules`): `{count} credit` / `{count} credite` / `{count} kredyty` / `{count} kreditů`;
- bannerul de consimțământ și panoul de cookie-uri;
- etichetele legale din subsol.

### Dacă vrei detecție după țară (nu după limbă)

Poți adăuga un pas înainte de `navigator.languages`, folosind un header pus de CDN,
fără JavaScript de geo-IP:

```js
// Cloudflare → Functions / Worker: citește request.cf.country și setează ?lang=xx
// sau trimite cookie-ul `country`; în frontend îl mapezi pe limbă.
```

Nu recomandăm geo-IP în browser: înseamnă o cerere către un terț pentru fiecare
vizitator nou, înainte de orice consimțământ.

## 3. Structura fișierelor

```text
frontend/src/i18n/
  index.js            # LOCALES, detectLocale, setLocale, t, formatDate, formatNumber, useI18n
  messages.js         # dicționarele, cheie de limbă
  locales/en.js       # 226 chei — limba de bază (fallback final)
  locales/ro.js       # 226 chei — limba de origine, completă
  locales/de.js …     # 50 chei — suprafața publică, restul cade pe engleză

frontend/src/legal/
  documents.js        # textele legale în română (versiunea care prevalează)
  documents.en.js     # traducerea în engleză, afișată pentru orice limbă ≠ ro
```

`t()` are lanțul de rezervă **limba curentă → engleză → română → cheia brută**,
deci o traducere incompletă afișează engleză, niciodată text gol sau
„landing.title” pe ecran.

### Acoperire curentă

```bash
cd frontend && node scripts/i18n-coverage.mjs
```

Rezultat: `en` și `ro` sunt complete (226 chei); celelalte 18 limbi acoperă ~22% —
adică tot ce vede un vizitator nou: antet, subsol, banner de cookie-uri, hero,
galerie, autentificare. Restul (studio, credite, lista job-urilor) apare în engleză.

Cheile care lipsesc dintr-o limbă sunt exact cele din grupurile `trial.*`,
`studio.*`, `jobs.*`, `credits.*`, `style.*`, `auth.*` (mai puțin taburile).

## 4. Cum adaugi o limbă

1. Copiază `frontend/src/i18n/locales/en.js` în `locales/xx.js` și tradu.
2. Adaugă `import xx from './locales/xx'` și `xx` în `messages.js`.
3. Adaugă intrarea în `LOCALES` din `index.js` (`{ code: 'xx', native: '…', english: '…' }`).
4. (Opțional) adaugă fusurile orare în `TIMEZONE_LOCALE`.
5. Rulează `npm run build` și `node scripts/i18n-coverage.mjs`.

## 5. Cum completezi o limbă existentă

Deschide `locales/xx.js` și adaugă doar cheile lipsite de acolo; nimic altceva nu
trebuie schimbat. Cele două fișiere de referință sunt `en.js` (termenii tehnici) și
`ro.js` (tonul editorial folosit în restul aplicației).

Reguli de ton:

- adresare directă, la persoana a II-a singular, ca în `ro.js`/`en.js`;
- „credits” este unitatea internă — nu se traduce ca „puncte”;
- numele stilurilor (`anime`, `manga`, `cyberpunk`) și numele produsului rămân neschimbate;
- termenii juridici se traduc natural, dar fără a schimba sensul (vezi `docs/09`).

## 6. Ce rămâne în română

| Zonă | Stare | De ce |
|---|---|---|
| Textele legale (`legal/documents.js` + `documents.en.js`) | **RO + EN** | `ro` primește versiunea română, orice altă limbă primește versiunea engleză; în caz de neconcordanță prevalează RO (`legal.notice`) |
| Panoul de administrare (`AdminPanel.vue`) | doar română | instrument intern, folosit doar de operator |
| Mesajele de eroare venite de la backend | doar engleză | sunt texte din API (`ApiError`), nu din frontend |

Când traduci documentele legale, păstrează aceeași structură de blocuri
(`p`, `ul`, `table`, `note`) și aceleași ancore de secțiune.

## 7. Verificare

| Ce | Cum |
|---|---|
| O limbă anume | `http://localhost:5174/?lang=de` |
| Detecția automată | schimbă limba browserului (sau fusul orar) și golește `localStorage` |
| Preferința salvată | alege o limbă, reîncarcă fără `?lang` |
| Plural corect | schimbă soldul de credite: 1 credit / 2 credite / 5 kredyty |
| `lang` corect în DOM | DevTools → `<html lang="…">` trebuie să se schimbe la fiecare selecție |
| Fallback | o cheie absentă trebuie să apară în engleză, nu ca `cheie.brută` |
