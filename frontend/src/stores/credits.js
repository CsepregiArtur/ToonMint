import { defineStore } from 'pinia'
import http from '../api/client'
import { useAuthStore } from './auth'

// Starea creditelor: catalogul de planuri/pachete și istoricul tranzacțiilor.
export const useCreditsStore = defineStore('credits', {
  state: () => ({
    catalog: { configured: false, entitlementId: '', plans: [], packs: [] },
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

    // Cere backendului linkul de checkout RevenueCat pentru un pachet de credite.
    async purchase(packId) {
      const { data } = await http.post('/credits/purchase', { packId })
      if (data.purchaseUrl) {
        window.open(data.purchaseUrl, '_blank', 'noopener')
      }
      return data
    },

    // Reîmprospătează profilul după ce RevenueCat confirmă plata prin webhook.
    async refreshProfile() {
      await useAuthStore().loadProfile()
    }
  }
})
