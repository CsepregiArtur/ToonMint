<script setup>
// Selector de stiluri artistice: o grilă de „chips-uri” în loc de un dropdown lung,
// ca toate cele peste 20 de stiluri să fie vizibile dintr-o privire.
defineProps({
  modelValue: { type: String, default: '' },
  styles: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue'])

// Selectează stilul apăsat.
function select(id) {
  emit('update:modelValue', id)
}
</script>

<template>
  <div class="style-grid" role="radiogroup" aria-label="Stil artistic">
    <button
      v-for="style in styles"
      :key="style.id"
      type="button"
      role="radio"
      class="style-chip"
      :class="{ active: style.id === modelValue }"
      :aria-checked="style.id === modelValue"
      :title="style.label"
      @click="select(style.id)"
    >
      <img
        v-if="style.previewUrl"
        class="style-thumb"
        :src="style.previewUrl"
        :alt="''"
        loading="lazy"
        @error="$event.target.style.display = 'none'"
      />
      <span>{{ style.label }}</span>
    </button>
  </div>
</template>
