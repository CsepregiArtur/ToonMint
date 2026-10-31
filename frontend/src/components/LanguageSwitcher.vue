<script setup>
import { LOCALES, locale, setLocale, t } from '../i18n'

// Selectorul de limbă. Valoarea aleasă se salvează și se reflectă imediat în
// toată interfața; `?lang=xx` rămâne linkul partajabil către o anumită limbă.
defineProps({
  compact: { type: Boolean, default: false }
})

function change(event) {
  setLocale(event.target.value)
}
</script>

<template>
  <label class="lang-switcher" :class="{ compact }">
    <span class="lang-globe" aria-hidden="true">🌐</span>
    <span class="sr-only">{{ t('common.language') }}</span>
    <select :value="locale" :aria-label="t('common.language')" @change="change">
      <option v-for="item in LOCALES" :key="item.code" :value="item.code">
        {{ item.native }}
      </option>
    </select>
  </label>
</template>

<style scoped>
.lang-switcher {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px 4px 10px;
  border: 1px solid rgba(39, 37, 49, 0.14);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.7);
  font-size: 0.82rem;
}

.lang-switcher.compact {
  padding: 2px 4px 2px 8px;
}

.lang-globe {
  font-size: 0.9em;
  line-height: 1;
}

select {
  border: none;
  background: transparent;
  color: inherit;
  font: inherit;
  padding: 2px 2px 2px 0;
  cursor: pointer;
  max-width: 11rem;
}

select:focus-visible {
  outline: 2px solid currentColor;
  border-radius: 6px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
  border: 0;
}
</style>
