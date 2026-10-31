import { defineStore } from 'pinia'
import http from '../api/client'

// Generarea gratuită, fără cont: o imagine per adresă de email.
export const useTrialStore = defineStore('trial', {
  state: () => ({
    step: 'idle',
    styleId: '',
    error: '',
    errorCode: '',
    job: null,
    publicId: '',
    beforeUrl: '',
    afterUrl: ''
  }),

  getters: {
    // Indică dacă imaginea gratuită a fost deja folosită pentru adresa curentă.
    trialUsed: (state) => state.errorCode === 'TRIAL_ALREADY_USED',
    busy: (state) => state.step === 'uploading' || state.step === 'processing'
  },

  actions: {
    // Setează stilul ales (folosit și de galeria de pe pagina de start).
    selectStyle(id) {
      this.styleId = id
    },

    // Verifică din timp dacă adresa mai are dreptul la imaginea gratuită.
    async isEligible(email) {
      if (!email) {
        return true
      }
      try {
        const { data } = await http.get('/public/trial/eligibility', { params: { email } })
        return data.eligible
      } catch (error) {
        return true
      }
    },

    // Trimite fotografia, așteaptă finalizarea și pregătește imaginile pentru slider.
    async generate({ email, file, styleId }) {
      this.error = ''
      this.errorCode = ''
      this.step = 'uploading'
      this.releaseUrls()

      // imaginea „înainte” pentru slider provine din fișierul ales de vizitator
      if (file) {
        this.beforeUrl = URL.createObjectURL(file)
      }

      const form = new FormData()
      form.append('image', file)
      form.append('email', email)
      form.append('style_id', styleId)

      try {
        const { data } = await http.post('/public/trial', form, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        this.publicId = data.publicId
        this.job = data
        this.step = 'processing'

        const finished = await this.poll(data.publicId)
        this.job = finished
        if (finished.status !== 'COMPLETED') {
          this.step = 'error'
          this.error = finished.errorMessage || 'Generarea a eșuat. Încearcă din nou.'
          return finished
        }

        const { data: blob } = await http.get(`/public/jobs/${data.publicId}/download`, {
          responseType: 'blob'
        })
        this.afterUrl = URL.createObjectURL(blob)
        this.step = 'done'
        return finished
      } catch (error) {
        this.step = 'error'
        this.errorCode = error.response?.data?.code || ''
        this.error = error.response?.data?.message || error.message
        throw error
      }
    },

    // Interoghează statusul jobului gratuit până ajunge într-o stare finală.
    async poll(publicId, timeoutMs = 120000) {
      const deadline = Date.now() + timeoutMs
      while (Date.now() < deadline) {
        const { data } = await http.get(`/public/jobs/${publicId}`)
        if (['COMPLETED', 'FAILED', 'REMOVED'].includes(data.status)) {
          return data
        }
        await new Promise((resolve) => setTimeout(resolve, 1200))
      }
      throw new Error('Generarea durează prea mult. Reîncarcă pagina și încearcă din nou.')
    },

    // Descarcă imaginea toon generată.
    async download() {
      if (!this.afterUrl) {
        return
      }
      const link = document.createElement('a')
      link.href = this.afterUrl
      link.download = `toonmint-${this.publicId}.png`
      document.body.appendChild(link)
      link.click()
      link.remove()
    },

    // Reia fluxul de la zero (păstrează adresa introdusă în componentă).
    reset() {
      this.step = 'idle'
      this.error = ''
      this.errorCode = ''
      this.job = null
      this.publicId = ''
      this.releaseUrls()
    },

    // Eliberează blobs-urile create local pentru a nu ține memoria ocupată.
    releaseUrls() {
      if (this.afterUrl) {
        URL.revokeObjectURL(this.afterUrl)
      }
      if (this.beforeUrl) {
        URL.revokeObjectURL(this.beforeUrl)
      }
      this.beforeUrl = ''
      this.afterUrl = ''
    }
  }
})
