import { defineStore } from 'pinia'
import http from '../api/client'
import { useAuthStore } from './auth'
import { SITE } from '../legal/site'

// Urmărirea plății: după checkout, webhook-ul RevenueCat poate întârzia câteva
// zeci de secunde, deci comparăm periodic profilul cu starea dinaintea plății.
const PAYMENT_POLL_INTERVAL = 3000
const PAYMENT_WATCH_TIMEOUT = 5 * 60 * 1000
const PENDING_PURCHASE_KEY = 'toonmint.pendingPurchase'
const PENDING_PURCHASE_TTL = 30 * 60 * 1000

let pollTimer = null
let pollTick = null
let visibilityHandler = null
let pollInFlight = false
let baseline = null
let watchStartedAt = 0

// Soldul dinaintea checkout-ului, păstrat și peste revenirea din pagina de plată.
function storePending(snapshot) {
  try {
    window.sessionStorage.setItem(PENDING_PURCHASE_KEY, JSON.stringify(snapshot))
  } catch (error) {
    // Stocare blocată (mod privat): urmărirea funcționează doar în acest tab.
  }
}

function readPending() {
  try {
    return JSON.parse(window.sessionStorage.getItem(PENDING_PURCHASE_KEY) || 'null')
  } catch (error) {
    return null
  }
}

// Starea creditelor: catalogul de planuri/pachete și istoricul tranzacțiilor.
export const useCreditsStore = defineStore('credits', {
  state: () => ({
    catalog: { configured: false, entitlementId: '', plans: [], packs: [] },
    payment: { productId: '', status: '', startedAt: 0 },
    transactions: [],
    totalTransactions: 0,
    loading: false,
    error: ''
  }),

  actions: {
    // Încarcă planurile de abonament și pachetele de credite, împreună cu linkurile RevenueCat.
    async loadCatalog() {
      this.loading = true
      this.error = ''
      try {
        const { data } = await http.get('/billing/catalog')
        this.catalog = data
      } catch (error) {
        this.error = error.message
      } finally {
        this.loading = false
      }
    },

    // Încarcă istoricul tranzacțiilor de credite al utilizatorului curent.
    async loadTransactions(page = 0, size = 20) {
      const { data } = await http.get('/credits/transactions', { params: { page, size } })
      this.transactions = data.items
      this.totalTransactions = data.totalItems
    },

    // Înregistrează confirmarea cerută înainte de checkout: livrare imediată +
    // renunțare la dreptul de retragere (OUG 34/2014 art. 16 lit. m).
    async acknowledge(productId) {
      const { data } = await http.post('/billing/acknowledgements', {
        productId,
        termsVersion: SITE.docVersion
      })
      return data
    },

    // Cere backendului linkul de checkout RevenueCat pentru un pachet de credite.
    async purchase(packId) {
      const snapshot = this.paymentSnapshot(packId)
      await this.acknowledge(packId)
      const { data } = await http.post('/credits/purchase', { packId })
      if (data.purchaseUrl) {
        window.open(data.purchaseUrl, '_blank', 'noopener')
        this.startPaymentWatch(snapshot)
      }
      return data
    },

    // Starea de referință față de care detectăm confirmarea plății.
    paymentSnapshot(productId) {
      const auth = useAuthStore()
      return {
        productId: productId || '',
        credits: auth.credits,
        subscriptionActive: Boolean(auth.user?.subscriptionActive),
        startedAt: Date.now()
      }
    },

    // Pornește verificarea periodică: creditele au crescut sau abonamentul s-a activat.
    startPaymentWatch(snapshot) {
      this.stopPaymentWatch()
      baseline = snapshot || this.paymentSnapshot('')
      watchStartedAt = baseline.startedAt || Date.now()
      this.payment = { productId: baseline.productId, status: 'waiting', startedAt: watchStartedAt }
      storePending(baseline)

      pollTick = () => {
        if (this.payment.status !== 'waiting' || pollInFlight) {
          return
        }
        pollInFlight = true
        this.checkPayment().finally(() => {
          pollInFlight = false
        })
      }
      visibilityHandler = () => {
        if (document.visibilityState === 'visible') {
          pollTick()
        }
      }

      pollTimer = window.setInterval(pollTick, PAYMENT_POLL_INTERVAL)
      window.addEventListener('focus', pollTick)
      document.addEventListener('visibilitychange', visibilityHandler)
      pollTick()
    },

    // Compară starea curentă cu cea dinaintea checkout-ului și încheie urmărirea.
    async checkPayment() {
      const auth = useAuthStore()
      await auth.loadProfile({ logoutOnError: false })
      const current = auth.user
      if (!current) {
        return
      }
      const creditsGrew = current.creditBalance > (baseline?.credits ?? 0)
      const becameActive = !baseline?.subscriptionActive && Boolean(current.subscriptionActive)
      if (creditsGrew || becameActive) {
        this.stopPaymentWatch()
        this.clearPendingPurchase()
        this.payment = { ...this.payment, status: 'confirmed' }
        await this.loadTransactions()
        return
      }
      if (Date.now() - watchStartedAt > PAYMENT_WATCH_TIMEOUT) {
        this.stopPaymentWatch()
        this.clearPendingPurchase()
        this.payment = { ...this.payment, status: 'timeout' }
      }
    },

    // Oprește temporizatorul și ascultătorii; starea `payment` rămâne neschimbată.
    stopPaymentWatch() {
      if (pollTimer) {
        window.clearInterval(pollTimer)
        pollTimer = null
      }
      if (pollTick) {
        window.removeEventListener('focus', pollTick)
        pollTick = null
      }
      if (visibilityHandler) {
        document.removeEventListener('visibilitychange', visibilityHandler)
        visibilityHandler = null
      }
    },

    clearPendingPurchase() {
      try {
        window.sessionStorage.removeItem(PENDING_PURCHASE_KEY)
      } catch (error) {
        // Nimic de curățat dacă stocarea este blocată.
      }
    },

    // Reia urmărirea după o revenire în pagină (inclusiv redirect de la checkout).
    resumePendingPurchase() {
      const snapshot = readPending()
      if (snapshot && snapshot.startedAt && Date.now() - snapshot.startedAt <= PENDING_PURCHASE_TTL) {
        this.startPaymentWatch(snapshot)
        return true
      }
      if (snapshot) {
        this.clearPendingPurchase()
      }
      return false
    },

    // Renunță la urmărire (utilizatorul închide mesajul sau anulează plata).
    dismissPayment() {
      this.stopPaymentWatch()
      this.clearPendingPurchase()
      this.payment = { productId: '', status: '', startedAt: 0 }
    },

    // Reîmprospătează profilul după ce RevenueCat confirmă plata prin webhook.
    async refreshProfile() {
      await useAuthStore().loadProfile()
    }
  }
})
