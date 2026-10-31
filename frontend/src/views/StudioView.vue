<script setup>
import { computed, onMounted } from 'vue'
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

// Reîmprospătează lista și profilul după ce un job nou a fost creat.
async function handleCreated() {
  await Promise.all([jobsStore.loadMine({ status: jobsStore.filterStatus }), auth.loadProfile()])
}

// Asigurăm datele proaspete la fiecare vizită.
onMounted(async () => {
  await auth.loadProfile()
  await jobsStore.loadMine()
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
