<script setup>
import { onMounted } from 'vue'
import JobUploader from '../components/JobUploader.vue'
import JobList from '../components/JobList.vue'
import { useJobsStore } from '../stores/jobs'
import { useAuthStore } from '../stores/auth'

// Pagina principală a utilizatorului: upload + lista job-urilor.
const jobsStore = useJobsStore()
const auth = useAuthStore()

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
        <h1>Studio</h1>
        <p class="muted">Încarcă o fotografie și alege stilul preferat.</p>
      </div>
      <span class="credit-pill">{{ auth.credits }} credite</span>
    </header>

    <JobUploader @created="handleCreated" />
    <JobList />
  </div>
</template>
