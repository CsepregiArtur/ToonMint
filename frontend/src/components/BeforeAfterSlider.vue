<script setup>
import { computed, ref } from 'vue'

// Slider comparativ „înainte / după”: o singură bară care descoperă imaginea toon
// peste fotografia originală. Funcționează cu mouse, touch și tastatură.
const props = defineProps({
  before: { type: String, required: true },
  after: { type: String, required: true },
  beforeLabel: { type: String, default: 'Original' },
  afterLabel: { type: String, default: 'Toon' },
  initial: { type: Number, default: 50 }
})

const position = ref(props.initial)
const dragging = ref(false)

// Procentul curent, limitat între 0 și 100.
const percent = computed(() => Math.min(100, Math.max(0, position.value)))

// Imaginea toon este vizibilă doar în partea stângă a barei.
const clipStyle = computed(() => ({ clipPath: `inset(0 ${100 - percent.value}% 0 0)` }))

// Calculează poziția din coordonata orizontală a pointerului.
function updateFromEvent(event) {
  const rect = event.currentTarget.getBoundingClientRect()
  const x = (event.clientX ?? event.touches?.[0]?.clientX ?? 0) - rect.left
  position.value = (x / rect.width) * 100
}

// Pornește glisarea la apăsare.
function startDrag(event) {
  dragging.value = true
  updateFromEvent(event)
  event.currentTarget.setPointerCapture?.(event.pointerId)
}

// Continuă glisarea cât timp pointerul este apăsat.
function onDrag(event) {
  if (!dragging.value) {
    return
  }
  updateFromEvent(event)
}

// Oprește glisarea.
function endDrag() {
  dragging.value = false
}
</script>

<template>
  <div
    class="ba-slider"
    :class="{ dragging }"
    @pointerdown="startDrag"
    @pointermove="onDrag"
    @pointerup="endDrag"
    @pointercancel="endDrag"
    @pointerleave="endDrag"
  >
    <img class="ba-image" :src="before" :alt="beforeLabel" draggable="false" />
    <img
      class="ba-image ba-after"
      :src="after"
      :alt="afterLabel"
      :style="clipStyle"
      draggable="false"
    />

    <span class="ba-badge left">{{ afterLabel }}</span>
    <span class="ba-badge right">{{ beforeLabel }}</span>

    <div class="ba-handle" :style="{ left: percent + '%' }">
      <span class="ba-knob" aria-hidden="true">⇆</span>
    </div>

    <input
      v-model.number="position"
      class="ba-range"
      type="range"
      min="0"
      max="100"
      step="1"
      aria-label="Compară fotografia originală cu varianta toon"
    />
  </div>
</template>
