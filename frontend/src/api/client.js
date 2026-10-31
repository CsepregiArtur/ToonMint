import axios from 'axios'

// Clientul HTTP comun pentru toate cererile către API.
// Adaugă automat tokenul JWT și normalizează mesajele de eroare venite de la server.
const http = axios.create({
  baseURL: '/api',
  timeout: 90000
})

// Interceptor de cerere: atașează tokenul JWT salvat în localStorage.
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('toonmint.token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Interceptor de răspuns: păstrează mesajul de eroare al backendului și curăță tokenul expirat.
http.interceptors.response.use(
  (response) => response,
  (error) => {
    const apiMessage = error.response?.data?.message
    if (apiMessage) {
      error.message = apiMessage
    }
    // Codul aplicativ (ex. EMAIL_NOT_VERIFIED, INVALID_CODE) e folosit de UI pentru fluxuri.
    const apiCode = error.response?.data?.code
    if (apiCode) {
      error.code = apiCode
    }
    if (error.response?.status === 401) {
      localStorage.removeItem('toonmint.token')
    }
    return Promise.reject(error)
  }
)

export default http
