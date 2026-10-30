import { defineStore } from 'pinia'
import http from '../api/client'
import { useAuthStore } from './auth'

// Starea job-urilor: lista utilizatorului, stările de încărcare și acțiunile CRUD.
export const useJobsStore = defineStore('jobs', {
  state: () => ({
    items: [],
    totalItems: 0,
    page: 0,
    size: 20,
    filterStatus: '',
    keyword: '',
    loading: false,
    error: '',
    styles: []
  }),

  actions: {
    // Încarcă stilurile disponibile pentru selectorul din formularul de upload.
    async loadStyles() {
      if (this.styles.length > 0) {
        return
      }
      const { data } = await http.get('/styles')
      this.styles = data
    },

    // Încarcă job-urile utilizatorului curent, opțional filtrate după status sau cuvânt cheie.
    async loadMine({ status = '', keyword = '', page = 0 } = {}) {
      const auth = useAuthStore()
      if (!auth.user) {
        await auth.loadProfile()
      }
      this.loading = true
      this.error = ''
      this.filterStatus = status
      this.keyword = keyword
      this.page = page
      try {
        const url = keyword ? '/jobs/search' : `/users/${auth.user.id}/jobs`
        const params = keyword
          ? { keyword, page, size: this.size }
          : { status: status || undefined, page, size: this.size }
        const { data } = await http.get(url, { params })
        this.items = data.items
        this.totalItems = data.totalItems
      } catch (error) {
        this.error = error.message
      } finally {
        this.loading = false
      }
    },

    // Trimite fotografia și stilul ales; serverul consumă un credit și pornește job-ul.
    async createJob({ file, styleId }) {
      const form = new FormData()
      form.append('image', file)
      form.append('style_id', styleId)
      const { data } = await http.post('/jobs', form, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      this.items = [data, ...this.items]
      await useAuthStore().loadProfile()
      return data
    },

    // Reîmprospătează un singur job până când ajunge într-o stare finală (COMPLETED/FAILED).
    async pollUntilFinished(jobId, timeoutMs = 120000) {
      const deadline = Date.now() + timeoutMs
      while (Date.now() < deadline) {
        const { data } = await http.get(`/jobs/${jobId}`)
        this.replaceJob(data)
        if (data.status === 'COMPLETED' || data.status === 'FAILED' || data.status === 'REMOVED') {
          await useAuthStore().loadProfile()
          return data
        }
        await new Promise((resolve) => setTimeout(resolve, 1500))
      }
      throw new Error('Jobul nu s-a terminat în timpul alocat. Reîncarcă lista.')
    },

    // Descarcă imaginea generată și o salvează local.
    async download(job) {
      const { data } = await http.get(`/jobs/${job.id}/download`, { responseType: 'blob' })
      const url = URL.createObjectURL(data)
      const link = document.createElement('a')
      link.href = url
      link.download = `toonmint-${job.id}.png`
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    },

    // Șterge (marchează REMOVED) un job al utilizatorului.
    async remove(jobId) {
      await http.delete(`/jobs/${jobId}`)
      this.items = this.items.filter((job) => job.id !== jobId)
    },

    // Reîncearcă un job eșuat, consumând un credit nou.
    async retry(jobId) {
      const { data } = await http.post(`/jobs/${jobId}/retry`)
      this.replaceJob(data)
      await useAuthStore().loadProfile()
      return data
    },

    // Înlocuiește un job din listă cu versiunea actualizată.
    replaceJob(updated) {
      this.items = this.items.map((job) => (job.id === updated.id ? updated : job))
    }
  }
})
