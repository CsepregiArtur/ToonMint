import axios from 'axios'
import { Capacitor } from '@capacitor/core'

// Clientul HTTP comun pentru toate cererile către API.
// Adaugă automat tokenul JWT și normalizează mesajele de eroare venite de la server.
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL

// Într-un WebView, `/api` ar fi relativ la `capacitor://localhost`, nu la server.
if (Capacitor.isNativePlatform() && !apiBaseUrl) {
  console.warn('VITE_API_BASE_URL is missing: native builds must point to the public API URL')
}

const http = axios.create({
  // În browser, `/api` ajunge la nginx; în aplicația nativă adresa completă vine
  // la build prin `VITE_API_BASE_URL`.
  baseURL: apiBaseUrl || '/api',
  timeout: 90000
})

const TOKEN_KEY = 'toonmint.token'
const REFRESH_KEY = 'toonmint.refreshToken'
const EXPIRES_KEY = 'toonmint.tokenExpiresAt'

// Tokenul de acces trăiește puțin, tokenul de refresh ține sesiunea între lansări.
// Ambele se păstrează în `localStorage`, care persistă în WKWebView și în WebView-ul
// Android — motivul pentru care nu s-a migrat la `@capacitor/preferences`.
export function readAccessToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function readRefreshToken() {
  return localStorage.getItem(REFRESH_KEY) || ''
}

// Momentul expirării tokenului de acces, în milisecunde (0 dacă nu e cunoscut).
export function accessTokenExpiresAt() {
  const value = localStorage.getItem(EXPIRES_KEY)
  return value ? Date.parse(value) || 0 : 0
}

// Salvează sesiunea primită de la server (JWT + token de refresh + expirare).
export function storeSession(payload) {
  if (!payload?.token) {
    return
  }
  localStorage.setItem(TOKEN_KEY, payload.token)
  if (payload.expiresAt) {
    localStorage.setItem(EXPIRES_KEY, payload.expiresAt)
  }
  if (payload.refreshToken) {
    localStorage.setItem(REFRESH_KEY, payload.refreshToken)
  }
}

// Șterge sesiunea locală.
export function clearSession() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(REFRESH_KEY)
  localStorage.removeItem(EXPIRES_KEY)
}

// Un singur refresh simultan: mai multe cereri care primesc 401 așteaptă același rezultat.
let pendingRefresh = null

// Schimbă tokenul de refresh pe un JWT nou. La eșec sesiunea locală este ștearsă.
export async function refreshSession() {
  const refreshToken = readRefreshToken()
  if (!refreshToken) {
    throw new Error('No refresh token available')
  }
  if (!pendingRefresh) {
    pendingRefresh = http
      .post('/auth/refresh', { refreshToken })
      .then(({ data }) => {
        storeSession(data)
        return data
      })
      .catch((error) => {
        clearSession()
        throw error
      })
      .finally(() => {
        pendingRefresh = null
      })
  }
  return pendingRefresh
}

// Indică dacă tokenul de acces a expirat sau expiră în curând.
export function accessTokenNeedsRefresh(skewSeconds = 60) {
  const expiresAt = accessTokenExpiresAt()
  return !expiresAt || expiresAt - Date.now() <= skewSeconds * 1000
}

// Interceptor de cerere: atașează tokenul JWT salvat în localStorage.
http.interceptors.request.use((config) => {
  const token = readAccessToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Interceptor de răspuns: păstrează mesajul de eroare al backendului, reînnoiește
// sesiunea o singură dată la 401 și curăță tokenul devenit inutilizabil.
http.interceptors.response.use(
  (response) => response,
  async (error) => {
    const status = error.response?.status
    const config = error.config || {}
    const isAuthCall = typeof config.url === 'string' && config.url.startsWith('/auth/')

    // 401 pe un endpoint protejat = token de acces expirat (după 24 de ore sau
    // după o repornire a aplicației). Se încearcă o singură reînnoire, apoi
    // cererea originală este reluată cu tokenul nou.
    if (status === 401 && !isAuthCall && !config.__retried && readRefreshToken()) {
      config.__retried = true
      try {
        await refreshSession()
        return http(config)
      } catch {
        // Sesiunea nu mai poate fi salvată: se raportează eroarea inițială.
      }
    }

    const apiMessage = error.response?.data?.message
    if (apiMessage) {
      error.message = apiMessage
    }
    // Codul aplicativ (ex. EMAIL_NOT_VERIFIED, INVALID_CODE) e folosit de UI pentru fluxuri.
    const apiCode = error.response?.data?.code
    if (apiCode) {
      error.code = apiCode
    }
    if (status === 401) {
      clearSession()
    }
    return Promise.reject(error)
  }
)

export default http
