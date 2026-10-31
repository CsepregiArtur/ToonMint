<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useJobsStore } from '../stores/jobs'
import StylePicker from './StylePicker.vue'
import { useAuthStore } from '../stores/auth'
import { useCreditsStore } from '../stores/credits'
import { t } from '../i18n'
import { isNative, takePhoto } from '../lib/native'

// Evenimentul `created` anunță pagina părinte că un job nou a fost trimis.
const emit = defineEmits(['created'])
const props = defineProps({ initialStyle: { type: String, default: '' } })

const jobsStore = useJobsStore()
const auth = useAuthStore()
const creditsStore = useCreditsStore()

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
  acceptSelection(selected)
}

// Aplică o fotografie venită din orice sursă (input de fișier sau camera nativă).
function acceptSelection(selected) {
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(selected.type)) {
    error.value = t('studio.errType')
    clearSelection()
    return
  }
  if (selected.size > 10 * 1024 * 1024) {
    error.value = t('studio.errSize')
    clearSelection()
    return
  }
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  file.value = selected
  previewUrl.value = URL.createObjectURL(selected)
}

// În aplicație, fotografia vine din cameră sau galerie prin plugin-ul nativ.
const nativeApp = isNative()
const cameraBusy = ref(false)

async function pickFromDevice() {
  error.value = ''
  cameraBusy.value = true
  try {
    const selected = await takePhoto()
    if (selected) {
      acceptSelection(selected)
    }
  } catch (photoError) {
    error.value = photoError.message
  } finally {
    cameraBusy.value = false
  }
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
    error.value = t('studio.errNoFile')
    return
  }
  if (!styleId.value) {
    error.value = t('studio.errNoStyle')
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
  await Promise.all([jobsStore.loadStyles(), creditsStore.loadCatalog()])
  if (!styleId.value && jobsStore.styles.length > 0) {
    styleId.value = jobsStore.styles.some((style) => style.id === props.initialStyle)
      ? props.initialStyle
      : jobsStore.styles[0].id
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
        <h2>{{ t('studio.uploadTitle') }}</h2>
      <p class="muted">{{ t('studio.uploadSubtitle') }}</p>
      </div>
      <span class="credit-pill" :class="{ empty: !hasCredits }">{{ t('common.credits', { count: auth.credits }) }}</span>
    </header>

    <div class="uploader-body">
      <label v-if="!nativeApp" class="dropzone" :class="{ filled: previewUrl }">
        <input
          ref="fileInput"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          class="file-input"
          @change="onFileChange"
        />
        <img v-if="previewUrl" :src="previewUrl" :alt="t('studio.previewAlt')" />
        <div v-else class="dropzone-placeholder">
          <span class="dropzone-icon">＋</span>
          <strong>{{ t('studio.choosePhoto') }}</strong>
          <span class="muted">{{ t('studio.format') }}</span>
        </div>
      </label>

      <button
        v-else
        type="button"
        class="dropzone"
        :class="{ filled: previewUrl }"
        :disabled="cameraBusy"
        @click="pickFromDevice"
      >
        <img v-if="previewUrl" :src="previewUrl" :alt="t('studio.previewAlt')" />
        <div v-else class="dropzone-placeholder">
          <span class="dropzone-icon">＋</span>
          <strong>{{ cameraBusy ? t('studio.cameraOpening') : t('studio.takePhoto') }}</strong>
          <span class="muted">{{ t('studio.format') }}</span>
        </div>
      </button>

      <div class="uploader-controls">
        <div class="field">
          <span>{{ t('studio.style', { count: jobsStore.styles.length }) }}</span>
          <StylePicker v-model="styleId" :styles="jobsStore.styles" />
        </div>

        <p class="hint">{{ t('studio.hint') }}</p>

        <p v-if="error" class="error">{{ error }}</p>
        <div v-if="!hasCredits" class="no-credit-offer">
          <p class="error">{{ t('studio.noCredits') }}</p>
          <div v-for="product in [...creditsStore.catalog.plans, ...creditsStore.catalog.packs].slice(0, 2)" :key="product.id" class="no-credit-product">
            <span><strong>{{ product.label }}</strong><small>{{ product.price }} · {{ product.credits }}</small></span>
            <router-link class="btn ghost small" to="/credits">{{ t('studio.see') }}</router-link>
          </div>
          <router-link class="credit-link" to="/credits">{{ t('studio.seeAll') }}</router-link>
        </div>

        <div class="actions">
          <button class="btn primary" :disabled="busy || !hasCredits" @click="submit">
            {{ busy ? t('studio.generating') : (selectedStyle ? t('studio.generateIn', { style: selectedStyle.label }) : t('studio.generate')) }}
          </button>
          <button v-if="file" class="btn ghost" :disabled="busy" @click="clearSelection">{{ t('studio.cancel') }}</button>
        </div>
      </div>
    </div>
  </section>
</template>
