import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Configurația Vite: serverul de dezvoltare trimite /api către backendul Spring Boot.
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
