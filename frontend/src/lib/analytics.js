// ---------------------------------------------------------------------------
// Etichete terțe (GTM, GA4, AdSense, Clarity) — încărcate EXCLUSIV după
// consimțământ. Fără ID configurat, eticheta respectivă nu face nimic.
//
// Regula de aur (ePrivacy / Legea 506/2004): niciun script de măsurare sau
// publicitate nu pleacă înainte ca vizitatorul să apese „Accept” în banner.
// De aceea aici nu există niciun <script> în index.html pentru ele.
// ---------------------------------------------------------------------------

const env = (key) => String(import.meta.env[key] || '').trim()

export const TAG_IDS = {
  gtm: env('VITE_GTM_ID'), // ex. GTM-XXXXXXX
  ga4: env('VITE_GA4_ID'), // ex. G-XXXXXXXXXX
  adsense: env('VITE_ADSENSE_CLIENT'), // ex. ca-pub-0000000000000000
  clarity: env('VITE_CLARITY_ID') // ex. z0u2mws6ze
}

export const ANY_TAG_CONFIGURED = Object.values(TAG_IDS).some(Boolean)

const loaded = new Set()

/** Creează `dataLayer` + funcția `gtag` dacă nu există deja. */
export function ensureGtag() {
  window.dataLayer = window.dataLayer || []
  if (typeof window.gtag !== 'function') {
    window.gtag = function gtag() {
      // gtag.js așteaptă obiectul `arguments`, nu un array.
      window.dataLayer.push(arguments)
    }
  }
  return window.gtag
}

/**
 * Starea inițială a Consent Mode v2: totul refuzat.
 * Se apelează înainte de montarea aplicației, ca nicio etichetă încărcată
 * ulterior să nu pornească cu consimțământ implicit.
 */
export function bootstrapConsentDefaults() {
  const gtag = ensureGtag()
  gtag('consent', 'default', {
    ad_storage: 'denied',
    ad_user_data: 'denied',
    ad_personalization: 'denied',
    analytics_storage: 'denied',
    functionality_storage: 'denied',
    personalization_storage: 'denied',
    security_storage: 'granted',
    wait_for_update: 500
  })
  // Fără cookie-uri, Google nu primește identificatori noi și nici parametri din URL.
  gtag('set', 'url_passthrough', false)
  gtag('set', 'ads_data_redaction', true)
}

/** Traduce categoriile noastre în semnalele Consent Mode. */
function consentSignals(categories) {
  return {
    ad_storage: categories.ads ? 'granted' : 'denied',
    ad_user_data: categories.ads ? 'granted' : 'denied',
    ad_personalization: categories.ads ? 'granted' : 'denied',
    analytics_storage: categories.analytics ? 'granted' : 'denied',
    functionality_storage: categories.analytics ? 'granted' : 'denied',
    personalization_storage: categories.ads ? 'granted' : 'denied',
    security_storage: 'granted'
  }
}

/** Trimite actualizarea de consimțământ către orice etichetă deja încărcată. */
export function applyConsent(categories) {
  const gtag = ensureGtag()
  gtag('consent', 'update', consentSignals(categories))
}

/** Încarcă eticheta permisă de categorii (idempotent). */
export function loadConsentGatedTags(categories) {
  if (categories.analytics || categories.ads) injectGtm()
  if (categories.analytics) {
    injectGa4()
    injectClarity()
  }
  if (categories.ads) injectAdsense()
}

function injectScript(id, src, options = {}) {
  if (!src || loaded.has(id) || document.getElementById(id)) return
  const script = document.createElement('script')
  script.id = id
  script.async = options.async !== false
  script.src = src
  if (options.crossOrigin) script.crossOrigin = options.crossOrigin
  document.head.appendChild(script)
  loaded.add(id)
}

function injectInline(id, code) {
  if (!code || loaded.has(id) || document.getElementById(id)) return
  const script = document.createElement('script')
  script.id = id
  script.text = code
  document.head.appendChild(script)
  loaded.add(id)
}

/** Google Tag Manager — containerul care distribuie restul etichetelor. */
export function injectGtm() {
  const id = TAG_IDS.gtm
  if (!id) return
  ensureGtag()
  window.dataLayer.push({ 'gtm.start': Date.now(), event: 'gtm.js' })
  injectScript('toonmint-gtm', `https://www.googletagmanager.com/gtm.js?id=${encodeURIComponent(id)}`)
}

/** GA4 direct (util când nu există container GTM). */
export function injectGa4() {
  const id = TAG_IDS.ga4
  if (!id) return
  const gtag = ensureGtag()
  injectScript('toonmint-gtag', `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(id)}`)
  gtag('js', new Date())
  gtag('config', id, { anonymize_ip: true })
}

/** Google AdSense. Fără un CMP certificat Google, în SEE se servesc doar anunțuri nepersonalizate. */
export function injectAdsense() {
  const client = TAG_IDS.adsense
  if (!client) return
  injectScript(
    'toonmint-adsense',
    `https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js?client=${encodeURIComponent(client)}`,
    { crossOrigin: 'anonymous' }
  )
}

/** Microsoft Clarity — hărți de căldură și înregistrări de sesiune. */
export function injectClarity() {
  const id = TAG_IDS.clarity
  if (!id) return
  injectInline(
    'toonmint-clarity',
    `(function(c,l,a,r,i,t,y){
      c[a]=c[a]||function(){(c[a].q=c[a].q||[]).push(arguments)};
      t=l.createElement(r);t.async=1;t.src="https://www.clarity.ms/tag/"+i;
      y=l.getElementsByTagName(r)[0];y.parentNode.insertBefore(t,y);
    })(window, document, "clarity", "script", ${JSON.stringify(id)});`
  )
}

// Cookie-uri first-party cunoscute, șterse când vizitatorul retrage acordul.
// Cele setate de Google pe domeniile lui (ex. `IDE`) nu pot fi șterse de aici —
// ele dispar la expirare sau prin setările de la adssettings.google.com.
const FIRST_PARTY_COOKIES = [
  '_ga', '_gid', '_gat', '_gcl_au', '_gcl_aw', '__gads', '__gpi', 'test_cookie',
  '_clck', '_clsk', 'CLID', 'MUID', 'ANONCHK', 'SM', '_dc_gtm'
]

function clearCookie(name) {
  const host = window.location.hostname
  const domains = ['', host, `.${host}`, `.${host.split('.').slice(-2).join('.')}`]
  for (const domain of domains) {
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/${domain ? `; domain=${domain}` : ''}`
  }
}

/** Șterge cookie-urile de măsurare/publicitate de pe domeniul nostru. */
export function clearTrackingCookies() {
  const names = document.cookie
    .split(';')
    .map((entry) => entry.split('=')[0].trim())
    .filter(Boolean)

  for (const name of names) {
    if (FIRST_PARTY_COOKIES.some((prefix) => name === prefix || name.startsWith(`${prefix}_`))) {
      clearCookie(name)
    }
  }
}
