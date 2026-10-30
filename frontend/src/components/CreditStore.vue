<script setup>
import { computed, onMounted, ref } from 'vue'
import { useCreditsStore } from '../stores/credits'
import { useAuthStore } from '../stores/auth'

// Soldul de credite, planurile de abonament, pachetele suplimentare și istoricul tranzacțiilor.
const creditsStore = useCreditsStore()
const auth = useAuthStore()

const actionError = ref('')
const busyPackId = ref('')

// Soldul curent afișat în antet.
const balance = computed(() => auth.credits)

// Eticheta lizibilă a tipului unei tranzacții.
const transactionLabels = {
  PURCHASE: 'Cumpărare',
  CONSUMPTION: 'Consum',
  REFUND: 'Rambursare',
  BONUS: 'Bonus',
  SUBSCRIPTION_GRANT: 'Abonament'
}

// Deschide checkout-ul RevenueCat pentru un pachet de credite.
async function buyPack(pack) {
  actionError.value = ''
  busyPackId.value = pack.id
  try {
    await creditsStore.purchase(pack.id)
  } catch (error) {
    actionError.value = error.message
  } finally {
    busyPackId.value = ''
  }
}

// Deschide linkul de abonament (plan săptămânal sau anual).
function openPlan(plan) {
  if (!plan.purchaseUrl) {
    actionError.value = 'Linkul RevenueCat nu este configurat pe acest mediu.'
    return
  }
  window.open(plan.purchaseUrl, '_blank', 'noopener')
}

// Reîmprospătează soldul și istoricul (după ce plata a fost confirmată prin webhook).
async function refreshAll() {
  await Promise.all([creditsStore.loadCatalog(), creditsStore.loadTransactions(), auth.loadProfile()])
}

// Încarcă datele necesare la montarea componentei.
onMounted(refreshAll)
</script>

<template>
  <section class="stack">
    <div class="card credit-hero">
      <div>
        <p class="muted">Sold curent</p>
        <h1 class="balance">{{ balance }} <span>credite</span></h1>
        <p class="muted">
          Abonament: <strong>{{ auth.user?.subscriptionType || 'FREE' }}</strong>
          <span v-if="auth.user?.subscriptionExpiresAt">
            · expiră {{ new Date(auth.user.subscriptionExpiresAt).toLocaleDateString('ro-RO') }}
          </span>
        </p>
      </div>
      <button class="btn ghost" @click="refreshAll">Reîmprospătează</button>
    </div>

    <p v-if="actionError" class="error">{{ actionError }}</p>
    <p v-if="creditsStore.error" class="error">{{ creditsStore.error }}</p>
    <p v-if="!creditsStore.catalog.configured" class="error">
      Plățile nu sunt configurate: setează REVENUECAT_PURCHASE_LINK_URL pe server.
    </p>

    <div class="card">
      <header class="card-header">
        <h2>Abonamente</h2>
        <span class="muted small">Deblochează generări săptămânale sau anuale</span>
      </header>
      <div class="catalog-grid">
        <article v-for="plan in creditsStore.catalog.plans" :key="plan.id" class="tile">
          <h3>{{ plan.label }}</h3>
          <p class="price">{{ plan.price }}</p>
          <p class="muted small">{{ plan.credits }} credite incluse</p>
          <button class="btn primary" :disabled="!plan.purchaseUrl" @click="openPlan(plan)">
            Abonează-te
          </button>
        </article>
      </div>
    </div>

    <div class="card">
      <header class="card-header">
        <h2>Pachete de credite</h2>
        <span class="muted small">Cumperi doar când ai nevoie</span>
      </header>
      <div class="catalog-grid">
        <article v-for="pack in creditsStore.catalog.packs" :key="pack.id" class="tile">
          <h3>{{ pack.label }}</h3>
          <p class="price">{{ pack.price }}</p>
          <p class="muted small">{{ pack.credits }} credite</p>
          <button
            class="btn primary"
            :disabled="busyPackId === pack.id || !pack.purchaseUrl"
            @click="buyPack(pack)"
          >
            {{ busyPackId === pack.id ? 'Se deschide…' : 'Cumpără' }}
          </button>
        </article>
      </div>
    </div>

    <div class="card">
      <header class="card-header">
        <h2>Istoric tranzacții</h2>
        <span class="muted small">{{ creditsStore.totalTransactions }} înregistrări</span>
      </header>
      <div v-if="creditsStore.transactions.length === 0" class="empty-state">
        Nicio tranzacție încă.
      </div>
      <table v-else class="table">
        <thead>
          <tr>
            <th>Data</th>
            <th>Tip</th>
            <th>Credite</th>
            <th>Detalii</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="entry in creditsStore.transactions" :key="entry.id">
            <td>{{ new Date(entry.createdAt).toLocaleString('ro-RO') }}</td>
            <td>{{ transactionLabels[entry.type] || entry.type }}</td>
            <td :class="entry.amount > 0 ? 'positive' : 'negative'">
              {{ entry.amount > 0 ? '+' : '' }}{{ entry.amount }}
            </td>
            <td class="muted small">{{ entry.description || '—' }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
