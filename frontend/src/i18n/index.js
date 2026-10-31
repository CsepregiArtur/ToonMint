import { ref } from 'vue'
import { messages } from './messages'

// ---------------------------------------------------------------------------
// Internaționalizare fără dependențe externe.
//
// Limba se alege în ordinea:
//   1. `?lang=xx` din URL            (link partajabil, ex. /?lang=de)
//   2. preferința salvată            (localStorage: toonmint.locale)
//   3. limbile browserului           (navigator.languages)
//   4. fusul orar                    (aproximare de țară, fără IP și fără API)
//   5. engleză
//
// Nu facem nicio cerere de geo-IP: fusul orar e disponibil local, deci alegerea
// limbii nu are nevoie de consimțământ și nu trimite date nimănui.
// ---------------------------------------------------------------------------

export const LOCALES = [
  { code: 'en', native: 'English', english: 'English' },
  { code: 'ro', native: 'Română', english: 'Romanian' },
  { code: 'fr', native: 'Français', english: 'French' },
  { code: 'de', native: 'Deutsch', english: 'German' },
  { code: 'es', native: 'Español', english: 'Spanish' },
  { code: 'it', native: 'Italiano', english: 'Italian' },
  { code: 'pt', native: 'Português', english: 'Portuguese' },
  { code: 'nl', native: 'Nederlands', english: 'Dutch' },
  { code: 'pl', native: 'Polski', english: 'Polish' },
  { code: 'cs', native: 'Čeština', english: 'Czech' },
  { code: 'sk', native: 'Slovenčina', english: 'Slovak' },
  { code: 'hu', native: 'Magyar', english: 'Hungarian' },
  { code: 'bg', native: 'Български', english: 'Bulgarian' },
  { code: 'hr', native: 'Hrvatski', english: 'Croatian' },
  { code: 'el', native: 'Ελληνικά', english: 'Greek' },
  { code: 'sv', native: 'Svenska', english: 'Swedish' },
  { code: 'da', native: 'Dansk', english: 'Danish' },
  { code: 'fi', native: 'Suomi', english: 'Finnish' },
  { code: 'nb', native: 'Norsk bokmål', english: 'Norwegian' },
  { code: 'tr', native: 'Türkçe', english: 'Turkish' }
]

const STORAGE_KEY = 'toonmint.locale'
const CODES = new Set(LOCALES.map((l) => l.code))

// Fus orar → limbă. Aproximare: e o preferință implicită, nu o decizie
// definitivă — vizitatorul poate schimba limba oricând.
const TIMEZONE_LOCALE = {
  'Europe/Bucharest': 'ro',
  'Europe/Chisinau': 'ro',
  'Europe/Paris': 'fr',
  'Europe/Monaco': 'fr',
  'Europe/Brussels': 'fr',
  'Europe/Berlin': 'de',
  'Europe/Vienna': 'de',
  'Europe/Zurich': 'de',
  'Europe/Madrid': 'es',
  'Europe/Rome': 'it',
  'Europe/Lisbon': 'pt',
  'Atlantic/Azores': 'pt',
  'Europe/Amsterdam': 'nl',
  'Europe/Warsaw': 'pl',
  'Europe/Prague': 'cs',
  'Europe/Bratislava': 'sk',
  'Europe/Budapest': 'hu',
  'Europe/Sofia': 'bg',
  'Europe/Zagreb': 'hr',
  'Europe/Athens': 'el',
  'Europe/Stockholm': 'sv',
  'Europe/Copenhagen': 'da',
  'Europe/Helsinki': 'fi',
  'Europe/Oslo': 'nb',
  'Europe/Istanbul': 'tr'
}

/** Limba curentă. Orice `t()` din template depinde de ea, deci se re-randează automat. */
export const locale = ref('en')

function normalize(code) {
  if (!code) return ''
  const base = String(code).toLowerCase().split(/[-_]/)[0]
  // „no” (norvegiană generică) și „nn” sunt mapate pe `nb`.
  if (base === 'no' || base === 'nn') return 'nb'
  return CODES.has(base) ? base : ''
}

function readStored() {
  try {
    return normalize(window.localStorage.getItem(STORAGE_KEY))
  } catch (error) {
    return ''
  }
}

function fromQuery() {
  try {
    return normalize(new URLSearchParams(window.location.search).get('lang'))
  } catch (error) {
    return ''
  }
}

function fromBrowser() {
  const list = navigator.languages && navigator.languages.length
    ? navigator.languages
    : [navigator.language]
  for (const lang of list) {
    const match = normalize(lang)
    if (match) return match
  }
  return ''
}

function fromTimezone() {
  try {
    const tz = Intl.DateTimeFormat().resolvedOptions().timeZone
    return TIMEZONE_LOCALE[tz] || ''
  } catch (error) {
    return ''
  }
}

/** Limba care ar fi aleasă dacă utilizatorul nu ar fi ales deja una. */
export function detectLocale() {
  return fromQuery() || readStored() || fromBrowser() || fromTimezone() || 'en'
}

/** Aplică limba, o reține și actualizează `<html lang>`. */
export function setLocale(code, { persist = true } = {}) {
  const next = normalize(code) || 'en'
  locale.value = next
  document.documentElement.setAttribute('lang', next)
  if (persist) {
    try {
      window.localStorage.setItem(STORAGE_KEY, next)
    } catch (error) {
      // Stocare blocată: limba rămâne valabilă doar pentru sesiunea curentă.
    }
  }
  return next
}

/** Se apelează o dată, la pornirea aplicației. */
export function initI18n() {
  if (fromQuery()) {
    setLocale(fromQuery())
  } else {
    setLocale(detectLocale(), { persist: Boolean(readStored()) })
  }
  return locale.value
}

function lookup(dict, key) {
  if (!dict) return undefined
  // Cheile sunt „plate” (ex. „landing.title”), deci căutăm direct.
  return dict[key]
}

/**
 * Traduce o cheie. Lanțul de rezervă este: limba curentă → engleză → română,
 * ca o limbă tradusă parțial să afișeze engleză, niciodată text gol.
 */
export function t(key, params = {}) {
  let value = lookup(messages[locale.value], key)
  if (value == null) value = lookup(messages.en, key)
  if (value == null) value = lookup(messages.ro, key)
  if (value == null) return key

  if (typeof value === 'object' && params.count != null) {
    let form = 'other'
    try {
      form = new Intl.PluralRules(locale.value).select(Number(params.count))
    } catch (error) {
      form = 'other'
    }
    value = value[form] ?? value.other ?? Object.values(value)[0]
  }
  if (typeof value !== 'string') return key

  return value.replace(/\{(\w+)\}/g, (match, name) =>
    params[name] == null ? match : String(params[name])
  )
}

export function formatDate(date, options = {}) {
  const value = date instanceof Date ? date : new Date(date)
  if (Number.isNaN(value.getTime())) return ''
  try {
    return new Intl.DateTimeFormat(locale.value, {
      day: 'numeric',
      month: 'long',
      year: 'numeric',
      ...options
    }).format(value)
  } catch (error) {
    return value.toISOString().slice(0, 10)
  }
}

export function formatNumber(value, options = {}) {
  try {
    return new Intl.NumberFormat(locale.value, options).format(value)
  } catch (error) {
    return String(value)
  }
}

/** Pentru componente: `const { t, locale, setLocale } = useI18n()`. */
export function useI18n() {
  return { t, locale, setLocale, locales: LOCALES, formatDate, formatNumber, detectLocale }
}
