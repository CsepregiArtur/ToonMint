<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useAdminStore } from '../stores/admin'

// Consola operatorului: toate job-urile, statistici, emailuri și evenimente RevenueCat.
const admin = useAdminStore()

const actionError = ref('')
const busy = ref(false)
const emailForm = reactive({
  selectedUserIds: [],
  extraEmails: '',
  subject: '',
  text: ''
})
const statsFrom = ref(defaultFrom())
const statsTo = ref(defaultTo())

// Data implicită de început pentru statistici (ultimele 7 zile).
function defaultFrom() {
  return new Date(Date.now() - 7 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
}

// Data implicită de sfârșit pentru statistici (azi).
function defaultTo() {
  return new Date().toISOString().slice(0, 10)
}

// Listează doar job-urile care pot fi modificate de operator.
const actionableStatuses = ['COMPLETED', 'FAILED', 'REMOVED']

// Construiește adresele introduse manual, separate prin virgulă sau spațiu.
const manualEmails = computed(() =>
  emailForm.extraEmails
    .split(/[\s,;]+/)
    .map((value) => value.trim())
    .filter(Boolean)
)

// Numărul total de destinatari selectați.
const recipientCount = computed(
  () => emailForm.selectedUserIds.length + manualEmails.value.length
)

// Marchează un job ca REMOVED.
async function markRemoved(job) {
  await runAction(() => admin.updateStatus(job.id, 'REMOVED'))
}

// Marchează un job ca FAILED (declanșează rambursarea creditului).
async function markFailed(job) {
  if (!window.confirm('Marchezi jobul ca eșuat? Creditul va fi rambursat utilizatorului.')) {
    return
  }
  await runAction(() => admin.updateStatus(job.id, 'FAILED'))
}

// Șterge definitiv metadatele jobului.
async function removeJob(job) {
  if (!window.confirm('Ștergi jobul din listă?')) {
    return
  }
  await runAction(() => admin.deleteJob(job.id))
}

// Trimite emailul către utilizatorii selectați și adresele introduse manual.
async function sendEmail() {
  actionError.value = ''
  if (recipientCount.value === 0) {
    actionError.value = 'Selectează cel puțin un destinatar.'
    return
  }
  busy.value = true
  try {
    const emails = manualEmails.value
    const users = admin.users.filter((user) => emailForm.selectedUserIds.includes(user.id))
    await admin.sendEmail({
      userIds: users.map((user) => user.id),
      emails,
      subject: emailForm.subject,
      text: emailForm.text
    })
    emailForm.subject = ''
    emailForm.text = ''
    emailForm.selectedUserIds = []
    emailForm.extraEmails = ''
  } catch (error) {
    actionError.value = error.message
  } finally {
    busy.value = false
  }
}

// Actualizează statisticile pentru intervalul selectat.
async function refreshStats() {
  actionError.value = ''
  try {
    await admin.loadStats(`${statsFrom.value}T00:00:00Z`, `${statsTo.value}T23:59:59Z`)
  } catch (error) {
    actionError.value = error.message
  }
}

// Reîncarcă toate datele consolei.
async function refreshAll() {
  actionError.value = ''
  await Promise.all([
    admin.loadJobs(),
    admin.loadUsers(),
    admin.loadEvents(),
    refreshStats()
  ])
}

// Execută o acțiune asupra unui job, afișând eventualele erori.
async function runAction(action) {
  actionError.value = ''
  busy.value = true
  try {
    await action()
  } catch (error) {
    actionError.value = error.message
  } finally {
    busy.value = false
  }
}

// Formatează procentajul de succes pentru afișare.
function successPercent() {
  if (!admin.stats) {
    return '—'
  }
  return `${Math.round(admin.stats.successRate * 100)}%`
}

// Încarcă datele inițiale la montarea componentei.
onMounted(refreshAll)
</script>

<template>
  <section class="stack">
    <header class="page-header">
      <div>
        <h1>Consolă operator</h1>
        <p class="muted">Aprobă, șterge job-uri și trimite emailuri către utilizatori.</p>
      </div>
      <button class="btn ghost" :disabled="busy" @click="refreshAll">Reîncarcă tot</button>
    </header>

    <p v-if="actionError" class="error">{{ actionError }}</p>
    <p v-if="admin.error" class="error">{{ admin.error }}</p>

    <div class="card">
      <header class="card-header">
        <h2>Statistici</h2>
        <div class="row">
          <input v-model="statsFrom" type="date" class="compact" />
          <input v-model="statsTo" type="date" class="compact" />
          <button class="btn ghost" @click="refreshStats">Actualizează</button>
        </div>
      </header>
      <div v-if="admin.stats" class="stat-grid">
        <div class="stat"><span class="muted">Job-uri</span><strong>{{ admin.stats.totalJobs }}</strong></div>
        <div class="stat"><span class="muted">Finalizate</span><strong>{{ admin.stats.completed }}</strong></div>
        <div class="stat"><span class="muted">Eșuate</span><strong>{{ admin.stats.failed }}</strong></div>
        <div class="stat"><span class="muted">Rată succes</span><strong>{{ successPercent() }}</strong></div>
        <div class="stat"><span class="muted">Credite consumate</span><strong>{{ admin.stats.creditsConsumed }}</strong></div>
      </div>
      <table v-if="admin.stats?.byStyle?.length" class="table">
        <thead>
          <tr><th>Stil</th><th>Job-uri</th><th>Finalizate</th><th>Eșuate</th><th>Credite</th></tr>
        </thead>
        <tbody>
          <tr v-for="row in admin.stats.byStyle" :key="row.styleId">
            <td>{{ row.styleId }}</td>
            <td>{{ row.jobs }}</td>
            <td>{{ row.completed }}</td>
            <td>{{ row.failed }}</td>
            <td>{{ row.credits }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="card">
      <header class="card-header">
        <h2>Toate job-urile</h2>
        <span class="muted small">{{ admin.totalJobs }} în total</span>
      </header>
      <table class="table">
        <thead>
          <tr><th>#</th><th>Stil</th><th>Status</th><th>Credite</th><th>Creat</th><th>Acțiuni</th></tr>
        </thead>
        <tbody>
          <tr v-for="job in admin.jobs" :key="job.id">
            <td>{{ job.id }}</td>
            <td>{{ job.styleLabel || job.styleId }}</td>
            <td><span class="badge" :class="`status-${job.status.toLowerCase()}`">{{ job.status }}</span></td>
            <td>{{ job.creditsConsumed }}</td>
            <td class="muted small">{{ new Date(job.createdAt).toLocaleString('ro-RO') }}</td>
            <td class="row">
              <button class="btn small ghost" :disabled="busy" @click="markFailed(job)">Marchează eșuat</button>
              <button class="btn small danger" :disabled="busy" @click="markRemoved(job)">Marchează șters</button>
              <button class="btn small danger" :disabled="busy" @click="removeJob(job)">Șterge</button>
            </td>
          </tr>
        </tbody>
      </table>
      <div v-if="!admin.jobs.length" class="empty-state">Nu există job-uri.</div>
    </div>

    <div class="card">
      <header class="card-header">
        <h2>Trimite email</h2>
        <span class="muted small">{{ recipientCount }} destinatari selectați</span>
      </header>
      <form class="form-grid" @submit.prevent="sendEmail">
        <label class="field span-2">
          <span>Utilizatori</span>
          <select v-model="emailForm.selectedUserIds" multiple size="6">
            <option v-for="user in admin.users" :key="user.id" :value="user.id">
              {{ user.email }} · {{ user.creditBalance }} credite
            </option>
          </select>
        </label>

        <label class="field span-2">
          <span>Adrese suplimentare (separate prin virgulă)</span>
          <input v-model="emailForm.extraEmails" placeholder="client1@example.com, client2@example.com" />
        </label>

        <label class="field span-2">
          <span>Subiect</span>
          <input v-model="emailForm.subject" required maxlength="200" />
        </label>

        <label class="field span-2">
          <span>Mesaj</span>
          <textarea v-model="emailForm.text" rows="5" required></textarea>
        </label>

        <div class="actions span-2">
          <button class="btn primary" :disabled="busy || recipientCount === 0">Trimite</button>
        </div>
      </form>
      <p v-if="admin.emailResult" class="success">
        Trimis: {{ admin.emailResult.sent }} / {{ admin.emailResult.requested }}
        <span v-if="admin.emailResult.simulated">(simulat — MAIL_ENABLED=false)</span>
      </p>
      <p v-if="admin.emailResult?.failures?.length" class="error small">
        Eșecuri: {{ admin.emailResult.failures.join('; ') }}
      </p>
    </div>

    <div class="card">
      <header class="card-header">
        <h2>Evenimente RevenueCat</h2>
        <span class="muted small">Ultimele webhook-uri primite</span>
      </header>
      <table v-if="admin.events.length" class="table">
        <thead>
          <tr><th>Tip</th><th>Produs</th><th>Status</th><th>Mesaj</th><th>Primit</th></tr>
        </thead>
        <tbody>
          <tr v-for="event in admin.events" :key="event.eventId">
            <td>{{ event.eventType }}</td>
            <td>{{ event.productId || '—' }}</td>
            <td>{{ event.status }}</td>
            <td class="muted small">{{ event.message || '—' }}</td>
            <td class="muted small">{{ new Date(event.receivedAt).toLocaleString('ro-RO') }}</td>
          </tr>
        </tbody>
      </table>
      <div v-else class="empty-state">Niciun eveniment încă.</div>
    </div>
  </section>
</template>
