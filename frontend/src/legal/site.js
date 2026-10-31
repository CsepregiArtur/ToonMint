// ---------------------------------------------------------------------------
// Datele juridice ale site-ului — sursa unică pentru paginile legale, footer și
// bannerul de cookie-uri. Completezi aici și se actualizează peste tot.
//
// Identificarea furnizorului este obligatorie (Legea 365/2002 art. 5, ANPC,
// GDPR art. 13). Dacă activitatea se desfășoară prin PFA/SRL, completează și
// `cui` + `regCom`: apar automat în paginile legale și în subsol.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Versiunea documentelor legale: o singură sursă (`.env` din rădăcina repo-ului).
// `vite.config.js` o injectează la build; fallback-ul acoperă rularea fișierelor
// fără bundler (scripturi Node, teste).
// ---------------------------------------------------------------------------
const FALLBACK_TERMS_VERSION = '2026-10-31'
const TERMS_VERSION =
  typeof __LEGAL_TERMS_VERSION__ === 'undefined' ? FALLBACK_TERMS_VERSION : __LEGAL_TERMS_VERSION__

// Data versiunii, scrisă în limba respectivă („31 octombrie 2026” / „31 October 2026”).
function versionLabel(iso, locale) {
  try {
    return new Intl.DateTimeFormat(locale, {
      day: 'numeric',
      month: 'long',
      year: 'numeric'
    }).format(new Date(iso))
  } catch (error) {
    return iso
  }
}

export const SITE = {
  product: 'ToonMint',
  // Domeniul public, folosit în textele legale.
  url: 'https://toonmint.cs-hub.xyz',
  // Operatorul de date / furnizorul serviciului.
  entity: {
    name: 'Csepregi Artur',
    // Lasă gol dacă vinzi ca persoană fizică. Pentru PFA/SRL completează ambele.
    cui: '',
    regCom: '',
    address: 'Satu Mare, România',
    phone: '+40 750 418 155',
    email: 'contact@cs-hub.xyz'
  },

  // Adresa pentru cereri GDPR (acces, ștergere, opoziție). Poate fi aceeași cu cea de contact.
  privacyEmail: 'contact@cs-hub.xyz',

  // Vârsta minimă pentru consimțământ valabil în România (art. 8 GDPR + art. 9 din
  // Legea 190/2018): 16 ani.
  minAge: 16,

  // Versiunea documentelor. Se afișează în pagini și se salvează în înregistrarea
  // consimțământului, ca să poți demonstra ce versiune a acceptat utilizatorul.
  // Valoarea vine din LEGAL_TERMS_VERSION (rădăcina repo-ului), injectată la build de
  // `vite.config.js` — aceeași pe care backendul o validează la înregistrare/checkout.
  docVersion: TERMS_VERSION,
  docVersionLabel: versionLabel(TERMS_VERSION, 'ro-RO'),
  docVersionLabelEn: versionLabel(TERMS_VERSION, 'en-GB'),

  // Termene reale, luate din configurația backendului. Dacă le schimbi în
  // `application.yml` (app.temp-store.ttl, app.jwt.ttl), actualizează și aici.
  retention: {
    tempImages: '10 minute după generare (cache temporar, apoi ștergere automată)',
    uploads: 'doar pe durata procesării, în același cache temporar',
    jobMetadata: 'până când ștergi jobul sau contul',
    account: 'până când ștergi contul',
    emailQueue: '30 de zile (coada de mesaje tranzacționale)',
    invoices: '10 ani (obligație contabilă — Legea 82/1991)',
    securityLogs: '12 luni'
  },

  // Autoritatea de supraveghere (ANSPDCP).
  dpa: {
    name: 'Autoritatea Națională de Supraveghere a Prelucrării Datelor cu Caracter Personal (ANSPDCP)',
    address: 'B-dul G-ral. Gheorghe Magheru 28-30, sector 1, București',
    url: 'https://www.dataprotection.ro'
  },

  // Soluționarea litigiilor pentru consumatori.
  anpc: {
    sal: 'https://anpc.ro/ce-este-sal/',
    sol: 'https://ec.europa.eu/consumers/odr'
  }
}

// Etichetele versiunilor publicate ale documentelor (afișate în pagini).
// `key` este cheia de traducere; eticheta se schimbă odată cu limba.
export const LEGAL_LINKS = [
  { to: '/legal/confidentialitate', key: 'legal.privacy' },
  { to: '/legal/cookies', key: 'legal.cookies' },
  { to: '/legal/termeni', key: 'legal.terms' },
  { to: '/legal/licente', key: 'legal.licenses' }
]
