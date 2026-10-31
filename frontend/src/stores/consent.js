import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { applyConsent, clearTrackingCookies, loadConsentGatedTags } from '../lib/analytics'
import { SITE } from '../legal/site'

// ---------------------------------------------------------------------------
// Consimțământul pentru cookie-uri.
//
// Salvăm local (localStorage) o înregistrare cu: versiunea documentului,
// momentul deciziei și categoriile acceptate. Versiunea se schimbă când
// modifici textele legale, ceea ce face ca bannerul să fie afișat din nou —
// exact ce cere principiul „consimțământ informat”.
// ---------------------------------------------------------------------------

export const CONSENT_KEY = 'toonmint.consent.v1'

// Consimțământul se reînnoiește la 6 luni (recomandarea ANSPDCP / EDPB).
const MAX_AGE_MS = 1000 * 60 * 60 * 24 * 180

export const EMPTY_CATEGORIES = { analytics: false, ads: false }

function readRecord() {
  try {
    const raw = window.localStorage.getItem(CONSENT_KEY)
    return raw ? JSON.parse(raw) : null
  } catch (error) {
    return null
  }
}

function isFresh(record) {
  if (!record || record.version !== SITE.docVersion) return false
  const decidedAt = Date.parse(record.decidedAt)
  if (Number.isNaN(decidedAt)) return false
  return Date.now() - decidedAt < MAX_AGE_MS
}

export const useConsentStore = defineStore('consent', () => {
  // Null = nu există o decizie validă → bannerul este vizibil.
  const record = ref(null)
  const settingsOpen = ref(false)
  const draft = ref({ ...EMPTY_CATEGORIES })
  const ready = ref(false)

  const decided = computed(() => record.value !== null)
  const categories = computed(() => record.value?.categories ?? { ...EMPTY_CATEGORIES })

  function persist(nextCategories) {
    const next = {
      version: SITE.docVersion,
      decidedAt: new Date().toISOString(),
      categories: { analytics: !!nextCategories.analytics, ads: !!nextCategories.ads }
    }
    record.value = next
    try {
      window.localStorage.setItem(CONSENT_KEY, JSON.stringify(next))
    } catch (error) {
      // Mod privat / stocare blocată: consimțământul rămâne valabil doar în sesiune.
    }
    applyConsent(next.categories)
    loadConsentGatedTags(next.categories)
    if (!next.categories.analytics && !next.categories.ads) {
      clearTrackingCookies()
    }
  }

  /** Se apelează o dată, la pornirea aplicației. */
  function init() {
    if (ready.value) return
    const stored = readRecord()
    ready.value = true
    if (isFresh(stored)) {
      record.value = stored
      applyConsent(stored.categories)
      loadConsentGatedTags(stored.categories)
    }
  }

  function acceptAll() {
    persist({ analytics: true, ads: true })
    settingsOpen.value = false
    return record.value
  }

  function rejectAll() {
    persist({ ...EMPTY_CATEGORIES })
    settingsOpen.value = false
    return record.value
  }

  function saveDraft() {
    persist(draft.value)
    settingsOpen.value = false
    return record.value
  }

  function openSettings() {
    draft.value = { ...categories.value }
    settingsOpen.value = true
  }

  function closeSettings() {
    settingsOpen.value = false
  }

  /** Retragerea completă a acordului (link „Retrage consimțământul”). */
  function withdraw() {
    persist({ ...EMPTY_CATEGORIES })
    settingsOpen.value = false
    return record.value
  }

  const decidedAtLabel = computed(() => {
    if (!record.value?.decidedAt) return ''
    const date = new Date(record.value.decidedAt)
    return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString('ro-RO')
  })

  return {
    record,
    draft,
    ready,
    decided,
    categories,
    decidedAtLabel,
    settingsOpen,
    init,
    acceptAll,
    rejectAll,
    saveDraft,
    openSettings,
    closeSettings,
    withdraw
  }
})
