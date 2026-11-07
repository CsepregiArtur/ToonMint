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

  getters: {
    // Există joburi încă în lucru: lista trebuie reîmprospătată automat, altfel
    // utilizatorul vede „Processing” până dă reload.
    hasActiveJobs: (state) =>
      state.items.some((job) => job.status === 'PENDING' || job.status === 'PROCESSING')
  },

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
    // `silent` reîmprospătează în fundal, fără să golească lista cu spinner-ul de încărcare.
    async loadMine({ status = '', keyword = '', page = 0, silent = false } = {}) {
      const auth = useAuthStore()
      if (!auth.user) {
        await auth.loadProfile()
      }
      if (!silent) {
        this.loading = true
        this.error = ''
      }
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
        // O reîmprospătare din fundal nu trebuie să ascundă lista deja afișată.
        if (!silent) {
          this.error = error.message
        }
      } finally {
        if (!silent) {
          this.loading = false
        }
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
    // Fereastra acoperă și cazul lent: serverul reîncearcă generarea de până la 5 ori,
    // cu un timeout de 60s pe încercare, deci un job legitim poate depăși 2 minute.
    async pollUntilFinished(jobId, timeoutMs = 300000) {
      const deadline = Date.now() + timeoutMs
      let consecutiveErrors = 0
      while (Date.now() < deadline) {
        try {
          const { data } = await http.get(`/jobs/${jobId}`)
          consecutiveErrors = 0
          this.replaceJob(data)
          if (data.status === 'COMPLETED' || data.status === 'FAILED' || data.status === 'REMOVED') {
            await useAuthStore().loadProfile()
            return data
          }
        } catch (error) {
          // Un request pierdut (rețea instabilă, redeploy) nu anulează așteptarea:
          // jobul continuă pe server. Renunțăm doar la erori repetate.
          consecutiveErrors += 1
          if (consecutiveErrors >= 5) {
            throw error
          }
        }
        await new Promise((resolve) => setTimeout(resolve, 1500))
      }
      // Jobul încă rulează: lista trebuie reîncărcată ca să arate starea reală.
      await this.loadMine({ status: this.filterStatus })
      throw new Error('Jobul încă se procesează pe server. Reîncarcă lista peste puțin timp.')
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
