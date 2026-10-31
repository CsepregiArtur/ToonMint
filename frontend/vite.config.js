import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

// Rădăcina repo-ului, calculată față de acest fișier (nu față de cwd).
const repoRoot = fileURLToPath(new URL('..', import.meta.url))

// Configurația Vite: serverul de dezvoltare trimite /api către backendul Spring Boot.
export default defineConfig(({ mode }) => {
  // Versiunea documentelor legale are o singură sursă: `.env` din rădăcina repo-ului,
  // pe care îl citește și backendul. Astfel nu poate exista un bundle care trimite o
  // altă versiune decât cea validată de server.
  const rootEnv = loadEnv(mode, repoRoot, '')
  const termsVersion =
    rootEnv.LEGAL_TERMS_VERSION || process.env.LEGAL_TERMS_VERSION || '2026-10-31'

  return {
    plugins: [vue()],
    define: {
      __LEGAL_TERMS_VERSION__: JSON.stringify(termsVersion)
    },
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: process.env.VITE_API_TARGET || 'http://localhost:8080',
          changeOrigin: true
        }
      }
    }
  }
})
