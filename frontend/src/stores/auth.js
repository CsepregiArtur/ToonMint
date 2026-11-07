import { defineStore } from 'pinia'
import http, {
  accessTokenNeedsRefresh,
  clearSession,
  readAccessToken,
  readRefreshToken,
  refreshSession,
  storeSession
} from '../api/client'
import { SITE } from '../legal/site'

// Starea de autentificare: tokenul JWT, profilul curent și acțiunile de login/register/logout.
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: readAccessToken(),
    user: null,
    loading: false,
    error: ''
  }),

  getters: {
    // Indică dacă utilizatorul este autentificat.
    isAuthenticated: (state) => Boolean(state.token),
    // Indică dacă utilizatorul are rol de operator.
    isAdmin: (state) => state.user?.role === 'ADMIN',
    // Soldul de credite afișat în bara de navigare.
    credits: (state) => state.user?.creditBalance ?? 0
  },

  actions: {
    // Salvează sesiunea primită de la server și profilul asociat.
    applySession(payload) {
      this.token = payload.token
      this.user = payload.user
      storeSession(payload)
    },

    // Reia sesiunea la pornirea aplicației: reînnoiește JWT-ul dacă a expirat
    // (normal după 24 de ore de la ultima folosire) și reîncarcă profilul.
    async restoreSession() {
      this.token = readAccessToken()
      if (!this.token) {
        return
      }
      if (accessTokenNeedsRefresh() && readRefreshToken()) {
        try {
          const session = await refreshSession()
          this.token = session.token
        } catch {
          this.token = ''
          return
        }
      }
      await this.loadProfile()
    },

    // Autentifică utilizatorul cu email și parolă.
    async login(email, password) {
      this.loading = true
      this.error = ''
      try {
        const { data } = await http.post('/auth/login', { email, password })
        this.applySession(data)
      } catch (error) {
        this.error = error.message
        throw error
      } finally {
        this.loading = false
      }
    },

    // Creează un cont nou; serverul trimite un cod de verificare pe email.
    // Nu autentifică imediat: contul trebuie confirmat cu codul primit.
    async register(email, password, displayName) {
      this.loading = true
      this.error = ''
      try {
        const { data } = await http.post('/auth/register', {
          email,
          password,
          displayName,
          // Dovada consimțământului: backendul salvează versiunea + momentul.
          acceptedTerms: true,
          termsVersion: SITE.docVersion
        })
        return data
      } catch (error) {
        this.error = error.message
        throw error
      } finally {
        this.loading = false
      }
    },

    // Confirmă adresa de email cu codul OTP și deschide sesiunea.
    async verifyEmail(email, code) {
      this.loading = true
      this.error = ''
      try {
        const { data } = await http.post('/auth/verify-email', { email, code })
        this.applySession(data)
      } catch (error) {
        this.error = error.message
        throw error
      } finally {
        this.loading = false
      }
    },

    // Retrimite codul de verificare a emailului.
    async resendVerification(email) {
      this.loading = true
      try {
        const { data } = await http.post('/auth/resend-verification', { email })
        return data
      } finally {
        this.loading = false
      }
    },

    // Cere un cod de resetare a parolei.
    async forgotPassword(email) {
      this.loading = true
      try {
        const { data } = await http.post('/auth/forgot-password', { email })
        return data
      } finally {
        this.loading = false
      }
    },

    // Setează parola nouă pe baza codului primit și deschide sesiunea.
    async resetPassword(email, code, newPassword) {
      this.loading = true
      this.error = ''
      try {
        const { data } = await http.post('/auth/reset-password', { email, code, newPassword })
        this.applySession(data)
      } catch (error) {
        this.error = error.message
        throw error
      } finally {
        this.loading = false
      }
    },

    // Login demo (doar în dezvoltare): un click, fără parolă.
    async demoLogin(role = 'USER') {
      this.loading = true
      this.error = ''
      try {
        const { data } = await http.post('/auth/demo-login', { role })
        this.applySession(data)
      } catch (error) {
        this.error = error.message
        throw error
      } finally {
        this.loading = false
      }
    },

    // Verifică dacă backendul rulează cu login demo activat.
    async demoAvailable() {
      try {
        const { data } = await http.get('/auth/demo-login')
        return data
      } catch (error) {
        return null
      }
    },

    // Reîncarcă profilul curent (credite, tip abonament) de la server.
    // `logoutOnError: false` păstrează sesiunea la o eroare tranzitorie (ex. polling de plată).
    async loadProfile({ logoutOnError = true } = {}) {
      if (!this.token) {
        return
      }
      try {
        const { data } = await http.get('/users/me')
        this.user = data
      } catch (error) {
        if (logoutOnError) {
          this.logout()
        }
      }
    },

    // Închide sesiunea: anulează și tokenul de refresh de pe server.
    logout() {
      const refreshToken = readRefreshToken()
      if (refreshToken) {
        // Fără `await`: ieșirea din cont trebuie să fie instantă, iar tokenul
        // expiră oricum dacă cererea nu ajunge la server.
        http.post('/auth/logout', { refreshToken }).catch(() => {})
      }
      this.token = ''
      this.user = null
      clearSession()
    }
  }
})
