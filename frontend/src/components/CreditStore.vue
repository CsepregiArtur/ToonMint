<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useCreditsStore } from '../stores/credits'
import { useAuthStore } from '../stores/auth'
import { locale, t } from '../i18n'

// Soldul de credite, planurile de abonament, pachetele suplimentare și istoricul tranzacțiilor.
const creditsStore = useCreditsStore()
const auth = useAuthStore()

const actionError = ref('')
const busyPackId = ref('')
// Confirmarea cerută de lege înainte de checkout (OUG 34/2014 art. 16 lit. m).
const acknowledged = ref(false)
// Explicația afișată când pachetele de credite sunt cerute fără abonament activ.
const subscriptionPrompt = ref(false)
const plansCard = ref(null)

// Soldul curent afișat în antet.
const balance = computed(() => auth.credits)

// Pachetele sunt un supliment pentru abonați, nu o alternativă la abonament.
const hasActiveSubscription = computed(() => Boolean(auth.user?.subscriptionActive))

let escHandler = null

// Deschide mesajul care îndrumă utilizatorul către planuri.
function openSubscriptionPrompt() {
  subscriptionPrompt.value = true
  if (!escHandler) {
    escHandler = (event) => {
      if (event.key === 'Escape') {
        closeSubscriptionPrompt()
      }
    }
    window.addEventListener('keydown', escHandler)
  }
}

function closeSubscriptionPrompt() {
  subscriptionPrompt.value = false
  if (escHandler) {
    window.removeEventListener('keydown', escHandler)
    escHandler = null
  }
}

// Închide mesajul și duce utilizatorul la planurile de abonament.
function showPlans() {
  closeSubscriptionPrompt()
  plansCard.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

onBeforeUnmount(() => {
  if (escHandler) {
    window.removeEventListener('keydown', escHandler)
  }
})

// Eticheta lizibilă a tipului unei tranzacții.
const transactionLabels = computed(() => ({
  PURCHASE: t('credits.type.PURCHASE'),
  CONSUMPTION: t('credits.type.CONSUMPTION'),
  REFUND: t('credits.type.REFUND'),
  BONUS: t('credits.type.BONUS'),
  SUBSCRIPTION_GRANT: t('credits.type.SUBSCRIPTION_GRANT')
}))

// Deschide checkout-ul RevenueCat pentru un pachet de credite.
// Ca și la planuri, fereastra se deschide sincron (altfel browserul blochează
// popup-ul), apoi primește adresa după ce confirmarea a fost înregistrată.
async function buyPack(pack) {
  actionError.value = ''
  if (!hasActiveSubscription.value) {
    openSubscriptionPrompt()
    return
  }
  if (!acknowledged.value) {
    actionError.value = t('credits.ackNeeded')
    return
  }
  if (!pack.purchaseUrl) {
    actionError.value = t('credits.noLink')
    return
  }
  busyPackId.value = pack.id
  const checkout = window.open('about:blank', '_blank')
  try {
    const { purchaseUrl, snapshot } = await creditsStore.purchase(pack.id)
    if (!purchaseUrl) {
      checkout?.close()
      actionError.value = t('credits.noLink')
      return
    }
    if (checkout) {
      checkout.opener = null
      checkout.location.href = purchaseUrl
    } else {
      window.location.href = purchaseUrl
    }
    creditsStore.startPaymentWatch(snapshot)
  } catch (error) {
    checkout?.close()
    // Serverul verifică din nou abonamentul: dacă a expirat între timp, arătăm același mesaj.
    if (error.code === 'SUBSCRIPTION_REQUIRED') {
      openSubscriptionPrompt()
    } else {
      actionError.value = error.message
    }
  } finally {
    busyPackId.value = ''
  }
}

// Deschide linkul de abonament (plan săptămânal sau anual).
// Fereastra se deschide sincron (altfel browserul blochează popup-ul), apoi primește
// adresa după ce confirmarea a fost înregistrată pe server.
async function openPlan(plan) {
  actionError.value = ''
  if (!acknowledged.value) {
    actionError.value = t('credits.ackNeeded')
    return
  }
  if (!plan.purchaseUrl) {
    actionError.value = t('credits.noLink')
    return
  }
  const checkout = window.open('about:blank', '_blank')
  const snapshot = creditsStore.paymentSnapshot(plan.id)
  try {
    await creditsStore.acknowledge(plan.id)
  } catch (error) {
    checkout?.close()
    actionError.value = error.message
    return
  }
  if (checkout) {
    checkout.opener = null
    checkout.location.href = plan.purchaseUrl
  } else {
    window.open(plan.purchaseUrl, '_blank', 'noopener')
  }
  creditsStore.startPaymentWatch(snapshot)
}

// Reîmprospătează soldul și istoricul (după ce plata a fost confirmată prin webhook).
async function refreshAll() {
  await Promise.all([creditsStore.loadCatalog(), creditsStore.loadTransactions(), auth.loadProfile()])
}

// Reia urmărirea plății după revenirea din checkout (`?purchase=success` vine din
// pagina de succes RevenueCat, dacă redirectul e configurat către /credits).
async function resumeAfterCheckout() {
  const status = new URLSearchParams(window.location.search).get('purchase')
  if (status === 'cancel') {
    creditsStore.dismissPayment()
  } else if (!creditsStore.resumePendingPurchase() && status === 'success') {
    creditsStore.startPaymentWatch(creditsStore.paymentSnapshot(''))
  }
  if (status) {
    const url = new URL(window.location.href)
    url.searchParams.delete('purchase')
    window.history.replaceState({}, '', url)
  }
}

// Încarcă datele necesare la montarea componentei.
onMounted(async () => {
  await refreshAll()
  await resumeAfterCheckout()
})
</script>

<template>
  <section class="stack">
    <div class="card credit-hero">
      <div>
        <p class="muted">{{ t('credits.balance') }}</p>
        <h1 class="balance">{{ t('common.credits', { count: balance }) }}</h1>
        <p class="muted">
          {{ t('credits.subscription') }}: <strong>{{ auth.user?.subscriptionType || 'FREE' }}</strong>
          <span v-if="auth.user?.subscriptionExpiresAt">
            {{ t('credits.expires', { date: new Date(auth.user.subscriptionExpiresAt).toLocaleDateString(locale) }) }}
          </span>
        </p>
      </div>
      <button class="btn ghost" @click="refreshAll">{{ t('credits.refresh') }}</button>
    </div>

    <p v-if="actionError" class="error">{{ actionError }}</p>
    <p v-if="creditsStore.error" class="error">{{ creditsStore.error }}</p>
    <p v-if="!creditsStore.catalog.configured" class="error">
      {{ t('credits.notConfigured') }}
    </p>

    <div v-if="creditsStore.payment.status === 'waiting'" class="payment-status hint">
      <span>{{ t('credits.paymentWaiting') }}</span>
      <button class="btn ghost small" @click="creditsStore.dismissPayment()">
        {{ t('credits.paymentDismiss') }}
      </button>
    </div>
    <div v-else-if="creditsStore.payment.status === 'confirmed'" class="payment-status success">
      <span>{{ t('credits.paymentConfirmed') }}</span>
      <button class="btn ghost small" @click="creditsStore.dismissPayment()">
        {{ t('credits.paymentDismiss') }}
      </button>
    </div>
    <div v-else-if="creditsStore.payment.status === 'timeout'" class="payment-status hint">
      <span>{{ t('credits.paymentPending') }}</span>
      <button class="btn ghost small" @click="creditsStore.dismissPayment()">
        {{ t('credits.paymentDismiss') }}
      </button>
    </div>

    <label class="legal-check ack-block">
      <input v-model="acknowledged" type="checkbox" />
      <span>
        {{ t('credits.ack') }}
        <router-link to="/legal/termeni" target="_blank">{{ t('legal.terms') }}</router-link>
      </span>
    </label>

    <div ref="plansCard" class="card">
      <header class="card-header">
        <h2>{{ t('credits.plans') }}</h2>
        <span class="muted small">{{ t('credits.plansNote') }}</span>
      </header>
      <div class="catalog-grid">
        <article v-for="plan in creditsStore.catalog.plans" :key="plan.id" class="tile">
          <h3>{{ plan.label }}</h3>
          <p class="price">{{ plan.price }}</p>
          <p class="muted small">{{ t('landing.pricing.line', { count: plan.credits }) }}</p>
          <button class="btn primary" :disabled="!acknowledged || !plan.purchaseUrl" @click="openPlan(plan)">
            {{ t('credits.subscribe') }}
          </button>
        </article>
      </div>
    </div>

    <div class="card">
      <header class="card-header">
        <h2>{{ t('credits.packs') }}</h2>
        <span class="muted small">{{ t('credits.packsNote') }}</span>
      </header>
      <div class="catalog-grid">
        <article v-for="pack in creditsStore.catalog.packs" :key="pack.id" class="tile">
          <h3>{{ pack.label }}</h3>
          <p class="price">{{ pack.price }}</p>
          <p class="muted small">{{ t('landing.pricing.line', { count: pack.credits }) }}</p>
          <button
            class="btn primary"
            :disabled="!acknowledged || busyPackId === pack.id || !pack.purchaseUrl"
            @click="buyPack(pack)"
          >
            {{ busyPackId === pack.id ? t('credits.opening') : t('credits.buy') }}
          </button>
        </article>
      </div>
    </div>

    <div class="card">
      <header class="card-header">
        <h2>{{ t('credits.history') }}</h2>
        <span class="muted small">{{ t('credits.records', { count: creditsStore.totalTransactions }) }}</span>
      </header>
      <div v-if="creditsStore.transactions.length === 0" class="empty-state">
        {{ t('credits.noTransactions') }}
      </div>
      <table v-else class="table">
        <thead>
          <tr>
            <th>{{ t('credits.col.date') }}</th>
            <th>{{ t('credits.col.type') }}</th>
            <th>{{ t('credits.col.credits') }}</th>
            <th>{{ t('credits.col.details') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in creditsStore.transactions" :key="entry.id">
            <td>{{ new Date(entry.createdAt).toLocaleString(locale) }}</td>
            <td>{{ transactionLabels[entry.type] || entry.type }}</td>
            <td :class="entry.amount > 0 ? 'positive' : 'negative'">
              {{ entry.amount > 0 ? '+' : '' }}{{ entry.amount }}
            </td>
            <td class="muted small">{{ entry.description || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="subscriptionPrompt" class="prompt-overlay" @click.self="closeSubscriptionPrompt">
      <div class="prompt-card" role="dialog" aria-modal="true" aria-labelledby="pack-subscription-title">
        <header class="prompt-head">
          <h2 id="pack-subscription-title">{{ t('credits.packsSubscribersOnly.title') }}</h2>
          <button
            type="button"
            class="prompt-close"
            :aria-label="t('common.close')"
            @click="closeSubscriptionPrompt"
          >
            ✕
          </button>
        </header>
        <p class="muted">{{ t('credits.packsSubscribersOnly.body') }}</p>
        <div class="prompt-actions">
          <button type="button" class="btn ghost" @click="closeSubscriptionPrompt">
            {{ t('common.close') }}
          </button>
          <button type="button" class="btn primary" @click="showPlans">
            {{ t('credits.packsSubscribersOnly.cta') }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.prompt-overlay {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(20, 16, 32, 0.45);
  backdrop-filter: blur(4px);
}

.prompt-card {
  width: min(460px, 100%);
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: var(--card);
  color: var(--text);
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.28);
}

.prompt-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.prompt-head h2 {
  margin: 0;
  font-size: 1.1rem;
}

.prompt-close {
  border: none;
  background: transparent;
  color: inherit;
  font-size: 1rem;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 8px;
}

.prompt-close:hover {
  background: rgba(39, 37, 49, 0.08);
}

.prompt-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}
</style>
