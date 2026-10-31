<script setup>
import { useRouter } from 'vue-router'
import { t } from '../i18n'

// Pagina 404: ruta catch-all din router. Rămâne publică, ca un link greșit
// să nu trimită vizitatorul (sau un crawler) la formularul de login.
const router = useRouter()

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'landing' })
  }
}
</script>

<template>
  <div class="stack not-found">
    <header class="page-header">
      <div>
        <p class="not-found-code">404</p>
        <h1>{{ t('notFound.title') }}</h1>
        <p class="muted">{{ t('notFound.subtitle') }}</p>
      </div>
    </header>
    <div class="not-found-actions">
      <router-link class="btn primary" :to="{ name: 'landing' }">
        {{ t('notFound.home') }}
      </router-link>
      <button type="button" class="btn ghost" @click="goBack">
        {{ t('notFound.back') }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  max-width: 620px;
  margin: 0 auto;
  padding: 80px 20px;
  text-align: center;
}

.not-found-code {
  margin: 0 0 8px;
  font-size: 3.4rem;
  font-weight: 800;
  line-height: 1;
  letter-spacing: -0.03em;
  background: linear-gradient(135deg, #ffbd59, #ff874c 52%, #f65e76);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}

.not-found h1 {
  margin: 0 0 8px;
}

.not-found-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: center;
}
</style>
