import { defineStore } from 'pinia'
import http from '../api/client'

// Starea de autentificare: tokenul JWT, profilul curent și acțiunile de login/register/logout.
export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('toonmint.token') || '',
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
    // Salvează tokenul primit de la server și profilul asociat.
    applySession(payload) {
      this.token = payload.token
      this.user = payload.user
      localStorage.setItem('toonmint.token', payload.token)
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

    // Creează un cont nou și autentifică utilizatorul imediat.
    async register(email, password, displayName) {
      this.loading = true
      this.error = ''
      try {
        const { data } = await http.post('/auth/register', { email, password, displayName })
        this.applySession(data)
      } catch (error) {
        this.error = error.message
        throw error
      } finally {
        this.loading = false
      }
    },

    // Reîncarcă profilul curent (credite, tip abonament) de la server.
    async loadProfile() {
      if (!this.token) {
        return
      }
      try {
        const { data } = await http.get('/users/me')
        this.user = data
      } catch (error) {
        this.logout()
      }
    },

    // Șterge sesiunea locală.
    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem('toonmint.token')
    }
  }
})
