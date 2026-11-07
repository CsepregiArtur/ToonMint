<script setup>
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import JobUploader from '../components/JobUploader.vue'
import JobList from '../components/JobList.vue'
import { useJobsStore } from '../stores/jobs'
import { useAuthStore } from '../stores/auth'
import { t } from '../i18n'

// Pagina principală a utilizatorului: upload + lista job-urilor.
const jobsStore = useJobsStore()
const auth = useAuthStore()
const route = useRoute()
const firstVisit = computed(() => jobsStore.totalItems === 0)

// Cât timp există joburi în lucru, lista se reîmprospătează singură, ca starea
// să treacă în COMPLETED/FAILED fără ca utilizatorul să apese „Refresh”.
const POLL_INTERVAL_MS = 3000
// Limită de siguranță: nu ținem un tab deschis la nesfârșit în polling dacă
// serverul nu mai răspunde cu o stare finală.
const POLL_MAX_MS = 15 * 60 * 1000
let pollTimer = null
let pollDeadline = 0

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function startPolling() {
  if (pollTimer) {
    return
  }
  pollDeadline = Date.now() + POLL_MAX_MS
  pollTimer = setInterval(async () => {
    if (Date.now() > pollDeadline) {
      stopPolling()
      return
    }
    // Tab ascuns: nu consumăm cereri; la revenire reîmprospătăm imediat.
    if (document.visibilityState === 'hidden') {
      return
    }
    const hadActive = jobsStore.hasActiveJobs
    await jobsStore.loadMine({ status: jobsStore.filterStatus, silent: true })
    if (!jobsStore.hasActiveJobs) {
      stopPolling()
      // Un job terminat poate schimba creditele (rambursare la FAILED).
      if (hadActive) {
        await auth.loadProfile()
      }
    }
  }, POLL_INTERVAL_MS)
}

// Pornește/oprește ciclul în funcție de ce e în listă.
watch(
  () => jobsStore.hasActiveJobs,
  (active) => (active ? startPolling() : stopPolling())
)

// La revenirea în tab, reîmprospătăm imediat: cât timp era ascuns nu am cerut nimic.
async function handleVisibility() {
  if (document.visibilityState === 'visible' && jobsStore.hasActiveJobs) {
    await jobsStore.loadMine({ status: jobsStore.filterStatus, silent: true })
    if (!jobsStore.hasActiveJobs) {
      stopPolling()
      await auth.loadProfile()
    }
  }
}

// Reîmprospătează lista și profilul după ce un job nou a fost creat.
async function handleCreated() {
  await Promise.all([jobsStore.loadMine({ status: jobsStore.filterStatus }), auth.loadProfile()])
}

// Asigurăm datele proaspete la fiecare vizită.
onMounted(async () => {
  document.addEventListener('visibilitychange', handleVisibility)
  await auth.loadProfile()
  await jobsStore.loadMine()
  if (jobsStore.hasActiveJobs) {
    startPolling()
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', handleVisibility)
  stopPolling()
})
</script>

<template>
  <div class="stack">
    <header class="page-header">
      <div>
        <h1>{{ t('studio.title') }}</h1>
        <p class="muted">{{ firstVisit ? t('studio.subtitleFirst') : t('studio.subtitle') }}</p>
      </div>
      <span class="credit-pill">{{ t('common.credits', { count: auth.credits }) }}</span>
    </header>

    <aside class="studio-nudge glass-panel">
      <span class="nudge-icon" aria-hidden="true">✦</span>
      <div><strong>{{ firstVisit ? t('studio.nudgeFirst') : t('studio.nudgeNext') }}</strong><p class="muted small">{{ t('studio.nudgeText') }}</p></div>
      <div class="row studio-nudge-actions">
        <a class="btn ghost small" href="#uploader">{{ firstVisit ? t('studio.startNow') : t('studio.createAgain') }}</a>
        <router-link class="btn ghost small" to="/#stiluri">{{ t('studio.inspiration') }}</router-link>
      </div>
    </aside>
    <JobUploader id="uploader" :initial-style="String(route.query.style || '')" @created="handleCreated" />
    <JobList />
  </div>
</template>
