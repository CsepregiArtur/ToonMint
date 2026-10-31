<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useJobsStore } from '../stores/jobs'
import http from '../api/client'
import { locale, t } from '../i18n'

// Lista job-urilor utilizatorului, cu filtrare după status și acțiuni de descărcare/ștergere/retry.
const jobsStore = useJobsStore()
const actionError = ref('')
const actionBusyId = ref(null)
const previewUrls = ref({})
const previewErrors = ref({})
const previewLoadingId = ref(null)
const openPreviewId = ref(null)

// Opțiunile de filtrare afișate în selector.
const statuses = ['', 'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'REMOVED']

// Eticheta prietenoasă pentru fiecare status (tradusă în limba curentă).
const statusLabels = computed(() => ({
  PENDING: t('jobs.status.PENDING'),
  PROCESSING: t('jobs.status.PROCESSING'),
  COMPLETED: t('jobs.status.COMPLETED'),
  FAILED: t('jobs.status.FAILED'),
  REMOVED: t('jobs.status.REMOVED')
}))

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
  if (!window.confirm(t('jobs.confirmRemove'))) {
    return
  }
  actionError.value = ''
  actionBusyId.value = job.id
  try {
    await jobsStore.remove(job.id)
    releasePreview(job.id)
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

// Formatează data ISO în limba curentă.
function formatDate(value) {
  if (!value) {
    return '—'
  }
  return new Date(value).toLocaleString(locale.value)
}

async function togglePreview(job) {
  if (openPreviewId.value === job.id) {
    openPreviewId.value = null
    return
  }
  openPreviewId.value = job.id
  if (previewUrls.value[job.id] || previewErrors.value[job.id]) return
  previewLoadingId.value = job.id
  try {
    const { data } = await http.get(`/jobs/${job.id}/download`, { responseType: 'blob' })
    previewUrls.value = { ...previewUrls.value, [job.id]: URL.createObjectURL(data) }
  } catch (error) {
    previewErrors.value = {
      ...previewErrors.value,
      [job.id]: t('jobs.previewGone')
    }
  } finally {
    previewLoadingId.value = null
  }
}

function releasePreview(jobId) {
  if (previewUrls.value[jobId]) URL.revokeObjectURL(previewUrls.value[jobId])
  const remaining = { ...previewUrls.value }
  delete remaining[jobId]
  previewUrls.value = remaining
  openPreviewId.value = null
}

onBeforeUnmount(() => Object.values(previewUrls.value).forEach((url) => URL.revokeObjectURL(url)))

// Încarcă job-urile utilizatorului la montarea componentei.
onMounted(() => jobsStore.loadMine())
</script>

<template>
  <section class="card">
    <header class="card-header">
      <div>
        <h2>{{ t('jobs.title') }}</h2>
        <p class="muted">{{ t('jobs.subtitle', { completed: completedCount, total: jobsStore.totalItems }) }}</p>
        <p class="muted small">{{ t('jobs.note') }}</p>
      </div>
      <div class="row">
        <select class="compact" :value="jobsStore.filterStatus" @change="changeFilter">
          <option v-for="status in statuses" :key="status" :value="status">
            {{ status ? statusLabels[status] : t('jobs.allStatuses') }}
          </option>
        </select>
        <button class="btn ghost" :disabled="jobsStore.loading" @click="refresh">{{ t('jobs.refresh') }}</button>
      </div>
    </header>

    <p v-if="actionError" class="error">{{ actionError }}</p>
    <p v-if="jobsStore.error" class="error">{{ jobsStore.error }}</p>

    <div v-if="jobsStore.loading" class="empty-state">{{ t('jobs.loading') }}</div>
    <div v-else-if="jobsStore.items.length === 0" class="empty-state">
      {{ t('jobs.empty') }}
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
        <div v-if="job.status === 'COMPLETED'" class="job-preview-wrap">
          <button
            class="btn small ghost"
            :aria-expanded="openPreviewId === job.id"
            @click="togglePreview(job)"
          >
            {{ previewLoadingId === job.id ? t('jobs.previewLoading') : openPreviewId === job.id ? t('jobs.previewHide') : t('jobs.previewShow') }}
          </button>
          <img v-if="openPreviewId === job.id && previewUrls[job.id]" class="job-preview" :src="previewUrls[job.id]" :alt="t('jobs.previewAlt', { style: job.styleLabel || job.styleId })" />
          <p v-if="previewErrors[job.id]" class="muted small">{{ previewErrors[job.id] }}</p>
        </div>
        <div class="row job-actions">
          <button
            v-if="job.status === 'COMPLETED'"
            class="btn small primary"
            :disabled="actionBusyId === job.id"
            @click="download(job)"
          >
            {{ t('trial.download') }}
          </button>
          <button
            v-if="job.status === 'FAILED'"
            class="btn small ghost"
            :disabled="actionBusyId === job.id"
            @click="retry(job)"
          >
            {{ t('jobs.retry') }}
          </button>
          <button
            v-if="job.status !== 'REMOVED'"
            class="btn small danger"
            :disabled="actionBusyId === job.id"
            @click="remove(job)"
          >
            {{ t('jobs.delete') }}
          </button>
        </div>
      </article>
    </div>
  </section>
</template>
