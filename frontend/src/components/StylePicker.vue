<script setup>
import { computed, ref } from 'vue'
import { t } from '../i18n'
// Selector de stiluri artistice: o grilă de „chips-uri” în loc de un dropdown lung,
// ca toate cele peste 20 de stiluri să fie vizibile dintr-o privire.
const props = defineProps({
  modelValue: { type: String, default: '' },
  styles: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])
const query = ref('')
const visibleStyles = computed(() => {
  const term = query.value.trim().toLocaleLowerCase()
  return term ? props.styles.filter((style) => style.label.toLocaleLowerCase().includes(term)) : props.styles
})

// Selectează stilul apăsat.
function select(id) {
  emit('update:modelValue', id)
}

function navigate(event, currentId) {
  const keys = ['ArrowRight', 'ArrowDown', 'ArrowLeft', 'ArrowUp']
  if (!keys.includes(event.key) || visibleStyles.value.length === 0) return
  event.preventDefault()
  const current = visibleStyles.value.findIndex((style) => style.id === currentId)
  const direction = ['ArrowRight', 'ArrowDown'].includes(event.key) ? 1 : -1
  const next = (current + direction + visibleStyles.value.length) % visibleStyles.value.length
  const target = visibleStyles.value[next]
  select(target.id)
  event.currentTarget.parentElement.querySelectorAll('[role="radio"]')[next]?.focus()
}
</script>

<template>
  <div class="style-picker">
  <label v-if="styles.length > 8" class="style-search">
    <span class="sr-only">{{ t('style.search') }}</span>
    <input v-model="query" type="search" :placeholder="t('style.searchPlaceholder')" autocomplete="off" />
  </label>
  <div class="style-grid" role="radiogroup" :aria-label="t('style.group')">
    <button
      v-for="style in visibleStyles"
      :key="style.id"
      type="button"
      role="radio"
      class="style-chip"
      :class="{ active: style.id === modelValue }"
      :aria-checked="style.id === modelValue"
      :title="style.label"
      @click="select(style.id)"
      @keydown="navigate($event, style.id)"
    >
      <img
        v-if="style.previewUrl"
        class="style-thumb"
        :src="style.previewUrl"
        alt=""
        loading="lazy"
        @error="$event.target.style.display = 'none'"
      />
      <span>{{ style.label }}</span>
    </button>
    <p v-if="visibleStyles.length === 0" class="muted small">{{ t('style.empty') }}</p>
  </div>
  </div>
</template>
