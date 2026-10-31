<script setup>
import CreditStore from '../components/CreditStore.vue'
import { t } from '../i18n'

// Pagina de credite: abonamente, pachete și istoric.
</script>

<template>
  <div class="stack">
    <header class="page-header">
      <div>
        <h1>{{ t('credits.title') }}</h1>
        <p class="muted">{{ t('credits.subtitle') }}</p>
      </div>
    </header>
    <CreditStore />
  </div>
</template>
