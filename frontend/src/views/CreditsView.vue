<script setup>
import CreditStore from '../components/CreditStore.vue'

// Pagina de credite: abonamente, pachete și istoric.
</script>

<template>
  <div class="stack">
    <header class="page-header">
      <div>
        <h1>Credite & abonamente</h1>
        <p class="muted">Plățile sunt procesate de RevenueCat; creditele apar imediat după confirmare.</p>
      </div>
    </header>
    <CreditStore />
  </div>
</template>
