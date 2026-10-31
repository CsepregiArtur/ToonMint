<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import http from '../api/client'
import { useAuthStore } from '../stores/auth'
import { formatDate, t } from '../i18n'

// Pagina de cont: exportul datelor (GDPR art. 20) și ștergerea contului (art. 17).
// Ambele sunt obligații legale, nu funcții opționale — de aceea stau într-o pagină
// permanent accesibilă, nu într-un flux ascuns.
const auth = useAuthStore()
const router = useRouter()

const exporting = ref(false)
const deleting = ref(false)
const confirmEmail = ref('')
const error = ref('')
const info = ref('')

const email = computed(() => auth.user?.email || '')
const canDelete = computed(
  () => !deleting.value && confirmEmail.value.trim().toLowerCase() === email.value.toLowerCase()
)

onMounted(() => auth.loadProfile())

// Descarcă un JSON cu toate datele păstrate despre utilizator.
async function exportData() {
  error.value = ''
  info.value = ''
  exporting.value = true
  try {
    const { data } = await http.get('/users/me/export')
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `toonmint-date-${new Date().toISOString().slice(0, 10)}.json`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
    info.value = t('account.exportDone')
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    exporting.value = false
  }
}

// Șterge contul după ce utilizatorul își scrie adresa de email.
async function deleteAccount() {
  error.value = ''
  info.value = ''
  if (!canDelete.value) {
    error.value = t('account.deleteMismatch')
    return
  }
  deleting.value = true
  try {
    await http.delete('/users/me')
    auth.logout()
    await router.push('/')
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    deleting.value = false
  }
}
</script>

<template>
  <div class="stack">
    <header class="page-header">
      <div>
        <h1>{{ t('account.title') }}</h1>
        <p class="muted">{{ t('account.subtitle') }}</p>
      </div>
    </header>

    <section class="card stack-sm">
      <div class="account-row">
        <span class="muted">{{ t('account.email') }}</span>
        <strong>{{ email || '—' }}</strong>
      </div>
      <div class="account-row">
        <span class="muted">{{ t('account.memberSince') }}</span>
        <strong>{{ auth.user?.createdAt ? formatDate(auth.user.createdAt) : '—' }}</strong>
      </div>
      <div class="account-row">
        <span class="muted">{{ t('account.balance') }}</span>
        <strong>{{ t('common.credits', { count: auth.credits }) }}</strong>
      </div>
      <div class="account-row">
        <span class="muted">{{ t('account.subscription') }}</span>
        <strong>{{ auth.user?.subscriptionType || 'FREE' }}</strong>
      </div>
    </section>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="info" class="muted small">{{ info }}</p>

    <section class="card stack-sm">
      <h2>{{ t('account.exportTitle') }}</h2>
      <p class="muted small">{{ t('account.exportText') }}</p>
      <div>
        <button class="btn ghost" :disabled="exporting" @click="exportData">
          {{ exporting ? t('account.exporting') : t('account.exportCta') }}
        </button>
      </div>
    </section>

    <section class="card stack-sm danger-zone">
      <h2>{{ t('account.deleteTitle') }}</h2>
      <p class="muted small">{{ t('account.deleteText') }}</p>
      <label class="field">
        <span>{{ t('account.deleteConfirm') }}</span>
        <input v-model="confirmEmail" type="email" autocomplete="off" :placeholder="email" />
      </label>
      <div>
        <button class="btn danger" :disabled="!canDelete" @click="deleteAccount">
          {{ deleting ? t('account.deleting') : t('account.deleteCta') }}
        </button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.account-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 24px;
  align-items: baseline;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(39, 37, 49, 0.08);
}

.account-row:last-child {
  padding-bottom: 0;
  border-bottom: none;
}

.danger-zone {
  border-color: rgba(217, 91, 69, 0.35);
}
</style>
