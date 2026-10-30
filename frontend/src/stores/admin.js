import { defineStore } from 'pinia'
import http from '../api/client'

// Starea consolei de operator: toate job-urile, utilizatorii, statisticile
// și evenimentele RevenueCat primite prin webhook.
export const useAdminStore = defineStore('admin', {
  state: () => ({
    jobs: [],
    totalJobs: 0,
    users: [],
    events: [],
    stats: null,
    loading: false,
    error: '',
    emailResult: null
  }),

  actions: {
    // Încarcă toate job-urile (opțional filtrate după status).
    async loadJobs(status = '', page = 0) {
      this.loading = true
      this.error = ''
      try {
        const { data } = await http.get('/jobs', {
          params: { status: status || undefined, page, size: 50 }
        })
        this.jobs = data.items
        this.totalJobs = data.totalItems
      } catch (error) {
        this.error = error.message
      } finally {
        this.loading = false
      }
    },

    // Încarcă lista de utilizatori pentru selectarea destinatarilor de email.
    async loadUsers() {
      const { data } = await http.get('/admin/users', { params: { page: 0, size: 100 } })
      this.users = data.items
    },

    // Încarcă statisticile agregate pentru intervalul dat.
    async loadStats(from, to) {
      const { data } = await http.get('/jobs/stats', { params: { from, to } })
      this.stats = data
    },

    // Încarcă ultimele evenimente RevenueCat pentru depanare.
    async loadEvents() {
      const { data } = await http.get('/admin/revenuecat-events', { params: { page: 0, size: 20 } })
      this.events = data.items
    },

    // Schimbă statusul unui job (COMPLETED / FAILED / REMOVED).
    async updateStatus(jobId, status) {
      const { data } = await http.put(`/jobs/${jobId}/status`, { status })
      this.jobs = this.jobs.map((job) => (job.id === jobId ? data : job))
      return data
    },

    // Șterge (marchează REMOVED) un job.
    async deleteJob(jobId) {
      await http.delete(`/jobs/${jobId}`)
      this.jobs = this.jobs.filter((job) => job.id !== jobId)
    },

    // Trimite un email către utilizatorii selectați prin endpointul de admin.
    async sendEmail(payload) {
      const { data } = await http.post('/admin/send-email', payload)
      this.emailResult = data
      return data
    }
  }
})
