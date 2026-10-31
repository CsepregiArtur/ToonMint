<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useJobsStore } from '../stores/jobs'
import StylePicker from './StylePicker.vue'
import { useAuthStore } from '../stores/auth'

// Evenimentul `created` anunță pagina părinte că un job nou a fost trimis.
const emit = defineEmits(['created'])

const jobsStore = useJobsStore()
const auth = useAuthStore()

const file = ref(null)
const previewUrl = ref('')
const styleId = ref('')
const busy = ref(false)
const error = ref('')
const fileInput = ref(null)

// Stilul selectat, folosit pentru eticheta butonului.
const selectedStyle = computed(() =>
  jobsStore.styles.find((style) => style.id === styleId.value)
)

// Verifică dacă utilizatorul are cel puțin un credit înainte de a trimite poza.
const hasCredits = computed(() => auth.credits > 0)

// Selectează fișierul, validează tipul și creează un preview local.
function onFileChange(event) {
  const selected = event.target.files?.[0]
  error.value = ''
  if (!selected) {
    return
  }
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(selected.type)) {
    error.value = 'Sunt acceptate doar imagini PNG, JPEG sau WEBP.'
    return
  }
  if (selected.size > 10 * 1024 * 1024) {
    error.value = 'Imaginea trebuie să aibă cel mult 10 MB.'
    return
  }
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  file.value = selected
  previewUrl.value = URL.createObjectURL(selected)
}

// Golește selecția curentă și preview-ul.
function clearSelection() {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  file.value = null
  previewUrl.value = ''
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

// Trimite fotografia către server, așteaptă rezultatul și anunță pagina părinte.
async function submit() {
  error.value = ''
  if (!file.value) {
    error.value = 'Alege o fotografie mai întâi.'
    return
  }
  if (!styleId.value) {
    error.value = 'Alege un stil.'
    return
  }
  busy.value = true
  try {
    const created = await jobsStore.createJob({ file: file.value, styleId: styleId.value })
    emit('created', created)
    clearSelection()
    await jobsStore.pollUntilFinished(created.id)
    await jobsStore.loadMine({ status: jobsStore.filterStatus })
  } catch (requestError) {
    error.value = requestError.message
  } finally {
    busy.value = false
  }
}

// Lansăm încărcarea stilurilor când componenta apare în pagină.
onMounted(async () => {
  await jobsStore.loadStyles()
  if (!styleId.value && jobsStore.styles.length > 0) {
    styleId.value = jobsStore.styles[0].id
  }
})

// Eliberăm memoria folosită de preview la demontarea componentei.
onBeforeUnmount(() => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
})
</script>

<template>
  <section class="card uploader">
    <header class="card-header">
      <div>
        <h2>Transformă o fotografie</h2>
        <p class="muted">Încarcă o poză, alege stilul și primești varianta cartoon în câteva secunde.</p>
      </div>
      <span class="credit-pill" :class="{ empty: !hasCredits }">{{ auth.credits }} credite</span>
    </header>

    <div class="uploader-body">
      <label class="dropzone" :class="{ filled: previewUrl }">
        <input
          ref="fileInput"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          hidden
          @change="onFileChange"
        />
        <img v-if="previewUrl" :src="previewUrl" alt="Previzualizare" />
        <div v-else class="dropzone-placeholder">
          <span class="dropzone-icon">＋</span>
          <strong>Alege o fotografie</strong>
          <span class="muted">PNG, JPEG sau WEBP · max 10 MB</span>
        </div>
      </label>

      <div class="uploader-controls">
        <div class="field">
          <span>Stil ({{ jobsStore.styles.length }} disponibile)</span>
          <StylePicker v-model="styleId" :styles="jobsStore.styles" />
        </div>

        <p class="hint">Fiecare generare consumă 1 credit. Dacă AI-ul eșuează, creditul se returnează automat.</p>

        <p v-if="error" class="error">{{ error }}</p>
        <p v-if="!hasCredits" class="error">
          Nu mai ai credite. Mergi la <router-link to="/credits">Credite</router-link> pentru a cumpăra.
        </p>

        <div class="actions">
          <button class="btn primary" :disabled="busy || !hasCredits" @click="submit">
            {{ busy ? 'Se generează…' : `Generează${selectedStyle ? ' · ' + selectedStyle.label : ''}` }}
          </button>
          <button v-if="file" class="btn ghost" :disabled="busy" @click="clearSelection">Renunță</button>
        </div>
      </div>
    </div>
  </section>
</template>
