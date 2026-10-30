<script setup>
import { computed, onMounted, ref } from 'vue'
import { useJobsStore } from '../stores/jobs'

// Lista job-urilor utilizatorului, cu filtrare după status și acțiuni de descărcare/ștergere/retry.
const jobsStore = useJobsStore()
const actionError = ref('')
const actionBusyId = ref(null)

// Opțiunile de filtrare afișate în selector.
const statuses = ['', 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REMOVED']

// Eticheta prietenoasă pentru fiecare status.
const statusLabels = {
  PENDING: 'În așteptare',
  PROCESSING: 'Se procesează',
  COMPLETED: 'Finalizat',
  FAILED: 'Eșuat',
  REMOVED: 'Șters'
}

// Numărul de job-uri finalizate, afișat în subtitlu.
const completedCount = computed(
  () => jobsStore.items.filter((job) => job.status === 'COMPLETED').length
)

// Schimbă filtrul de status și reîncarcă lista.
async function changeFilter(event) {
  await jobsStore.loadMine({ status: event.target.value })
}

// Reîncarcă lista păstrând filtrul curent.
async function refresh() {
  await jobsStore.loadMine({ status: jobsStore.filterStatus })
}

// Descarcă imaginea generată pentru un job finalizat.
async function download(job) {
  actionError.value = ''
  actionBusyId.value = job.id
  try {
    await jobsStore.download(job)
  } catch (error) {
    actionError.value = error.message
  } finally {
    actionBusyId.value = null
  }
}

// Șterge job-ul după confirmarea utilizatorului.
async function remove(job) {
  if (!window.confirm('Ștergi acest job? Imaginea temporară va fi eliminată.')) {
    return
  }
  actionError.value = ''
  actionBusyId.value = job.id
  try {
    await jobsStore.remove(job.id)
  } catch (error) {
    actionError.value = error.message
  } finally {
    actionBusyId.value = null
  }
}

// Reîncearcă un job eșuat și reîmprospătează starea.
async function retry(job) {
  actionError.value = ''
  actionBusyId.value = job.id
  try {
    const retried = await jobsStore.retry(job.id)
    await jobsStore.pollUntilFinished(retried.id)
    await jobsStore.loadMine({ status: jobsStore.filterStatus })
  } catch (error) {
    actionError.value = error.message
  } finally {
    actionBusyId.value = null
  }
}

// Formatează data ISO într-un text local lizibil.
function formatDate(value) {
  if (!value) {
    return '—'
  }
  return new Date(value).toLocaleString('ro-RO')
}

// Încarcă job-urile utilizatorului la montarea componentei.
onMounted(() => jobsStore.loadMine())
</script>

<template>
  <section class="card">
    <header class="card-header">
      <div>
        <h2>Job-urile mele</h2>
        <p class="muted">{{ completedCount }} finalizate · {{ jobsStore.totalItems }} în total</p>
      </div>
      <div class="row">
        <select class="compact" :value="jobsStore.filterStatus" @change="changeFilter">
          <option v-for="status in statuses" :key="status" :value="status">
            {{ status ? statusLabels[status] : 'Toate statusurile' }}
          </option>
        </select>
        <button class="btn ghost" :disabled="jobsStore.loading" @click="refresh">Reîncarcă</button>
      </div>
    </header>

    <p v-if="actionError" class="error">{{ actionError }}</p>
    <p v-if="jobsStore.error" class="error">{{ jobsStore.error }}</p>

    <div v-if="jobsStore.loading" class="empty-state">Se încarcă job-urile…</div>
    <div v-else-if="jobsStore.items.length === 0" class="empty-state">
      Nicio generare încă. Încarcă o fotografie mai sus.
    </div>

    <div v-else class="job-grid">
      <article v-for="job in jobsStore.items" :key="job.id" class="job-card">
        <div class="job-top">
          <span class="badge" :class="`status-${job.status.toLowerCase()}`">
            {{ statusLabels[job.status] || job.status }}
          </span>
          <span class="muted small">#{{ job.id }}</span>
        </div>
        <h3>{{ job.styleLabel || job.styleId }}</h3>
        <p class="muted small">{{ formatDate(job.createdAt) }}</p>
        <p v-if="job.errorMessage" class="error small">{{ job.errorMessage }}</p>
        <div class="row job-actions">
          <button
            v-if="job.status === 'COMPLETED'"
            class="btn small primary"
            :disabled="actionBusyId === job.id"
            @click="download(job)"
          >
            Descarcă
          </button>
          <button
            v-if="job.status === 'FAILED'"
            class="btn small ghost"
            :disabled="actionBusyId === job.id"
            @click="retry(job)"
          >
            Reîncearcă
          </button>
          <button
            v-if="job.status !== 'REMOVED'"
            class="btn small danger"
            :disabled="actionBusyId === job.id"
            @click="remove(job)"
          >
            Șterge
          </button>
        </div>
      </article>
    </div>
  </section>
</template>
