<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useTrialStore } from '../stores/trial'
import BeforeAfterSlider from './BeforeAfterSlider.vue'
import StylePicker from './StylePicker.vue'
import http from '../api/client'
import { t } from '../i18n'

// Fluxul gratuit: email + fotografie + stil → o imagine toon, fără cont.
const trial = useTrialStore()

const email = ref('')
const file = ref(null)
const previewUrl = ref('')
const fileInput = ref(null)
const localError = ref('')
const styles = ref([])
const eligibility = ref(null)

// Stilul selectat, folosit pe buton.
const selectedStyle = computed(() => styles.value.find((style) => style.id === trial.styleId))
const signupTarget = computed(() => ({
  path: '/login',
  query: {
    mode: 'register',
    redirect: `/studio?style=${encodeURIComponent(trial.styleId || '')}`
  }
}))

// Previzualizarea afișată: fișierul ales sau imaginea deja trimisă (după o eroare).
const previewSrc = computed(() => previewUrl.value || trial.beforeUrl)

// Verifică la blur dacă adresa mai poate primi imaginea gratuită.
async function checkEmail() {
  eligibility.value = null
  if (!email.value || !email.value.includes('@')) {
    return
  }
  eligibility.value = await trial.isEligible(email.value)
}

// Validează fișierul ales și creează previzualizarea locală.
function onFileChange(event) {
  const selected = event.target.files?.[0]
  localError.value = ''
  if (!selected) {
    return
  }
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(selected.type)) {
    localError.value = t('studio.errType')
    trial.releaseUrls()
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
    file.value = null
    event.target.value = ''
    return
  }
  if (selected.size > 10 * 1024 * 1024) {
    localError.value = t('studio.errSize')
    trial.releaseUrls()
    if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
    previewUrl.value = ''
    file.value = null
    event.target.value = ''
    return
  }
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  file.value = selected
  previewUrl.value = URL.createObjectURL(selected)
}

// Trimite cererea de trial; erorile sunt afișate în formular.
async function submit() {
  localError.value = ''
  if (!email.value.includes('@')) {
    localError.value = t('trial.needEmail')
    return
  }
  if (!file.value) {
    localError.value = t('studio.errNoFile')
    return
  }
  if (!trial.styleId) {
    localError.value = t('studio.errNoStyle')
    return
  }
  try {
    await trial.generate({ email: email.value, file: file.value, styleId: trial.styleId })
  } catch (error) {
    if (trial.trialUsed) {
      eligibility.value = false
    }
  }
}

// Reia fluxul cu o altă fotografie (aceeași adresă, dacă mai are dreptul).
function startOver() {
  trial.reset()
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  previewUrl.value = ''
  file.value = null
  if (fileInput.value) {
    fileInput.value.value = ''
  }
}

// Încarcă stilurile publice la montare.
onMounted(async () => {
  try {
    const { data } = await http.get('/styles')
    styles.value = data
    if (data.length > 0 && !trial.styleId) {
      trial.styleId = data[0].id
    }
  } catch (error) {
    localError.value = t('trial.stylesFailed')
  }
})

// Curăță blobs-urile la demontare.
onBeforeUnmount(() => {
  if (previewUrl.value) {
    URL.revokeObjectURL(previewUrl.value)
  }
  trial.releaseUrls()
})
</script>

<template>
  <section id="incearca" class="card trial-card">
    <header class="card-header">
      <div>
        <h2>{{ t('trial.title') }}</h2>
        <p class="muted">{{ t('trial.subtitle') }}</p>
      </div>
      <span class="trial-pill">{{ t('trial.badge') }}</span>
    </header>

    <div v-if="trial.step === 'done'" class="result">
      <BeforeAfterSlider
        :before="trial.beforeUrl"
        :after="trial.afterUrl"
        :before-label="t('trial.before')"
        :after-label="t('trial.after')"
        :initial="55"
      />
      <div class="result-actions">
        <button class="btn primary" @click="trial.download()">{{ t('trial.download') }}</button>
        <button class="btn ghost" @click="startOver">{{ t('trial.another') }}</button>
      </div>
      <div class="upsell">
        <div>
          <strong>{{ t('trial.moreTitle') }}</strong>
          <p class="muted small">{{ t('trial.moreText') }}</p>
        </div>
        <div class="row">
          <router-link class="btn primary" :to="signupTarget">{{ t('trial.signup') }}</router-link>
          <router-link class="btn ghost" to="/login">{{ t('trial.haveAccount') }}</router-link>
        </div>
      </div>
    </div>

    <div v-else-if="trial.busy" class="trial-busy">
      <div class="spinner" aria-hidden="true"></div>
      <p class="busy-title">{{ trial.step === 'uploading' ? t('trial.busyUpload') : t('trial.busyProcess') }}</p>
      <ol class="generation-steps" aria-live="polite">
        <li class="complete">{{ t('trial.step1') }}</li>
        <li :class="{ complete: trial.step === 'processing', current: trial.step === 'uploading' }">{{ t('trial.step2') }}</li>
        <li :class="{ current: trial.step === 'processing' }">
          {{ t('trial.step3', { style: selectedStyle?.label || 'toon' }) }}
        </li>
      </ol>
      <p class="muted small">{{ t('trial.wait') }}</p>
    </div>

    <form v-else class="trial-form" @submit.prevent="submit">
      <label class="dropzone compact" :class="{ filled: previewSrc }">
        <input
          ref="fileInput"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          class="file-input"
          @change="onFileChange"
        />
        <img v-if="previewSrc" :src="previewSrc" :alt="t('trial.previewAlt')" />
        <div v-if="previewSrc" class="preview-replace">{{ t('trial.replace') }}</div>
        <div v-else class="dropzone-placeholder">
          <span class="dropzone-icon">＋</span>
          <strong>{{ t('trial.choosePhoto') }}</strong>
          <span class="muted small">{{ t('trial.format') }}</span>
        </div>
      </label>

      <div class="trial-fields">
        <label class="field">
          <span>{{ t('trial.emailLabel') }}</span>
          <input
            v-model.trim="email"
            type="email"
            autocomplete="email"
            :placeholder="t('trial.emailPlaceholder')"
            required
            @blur="checkEmail"
          />
        </label>
        <p v-if="eligibility === false" class="error small">
          {{ t('trial.used') }}
          <router-link :to="signupTarget">{{ t('trial.createAccount') }}</router-link>
          
        </p>

        <div class="field">
          <span>{{ t('trial.styleLabel', { count: styles.length }) }}</span>
          <StylePicker v-model="trial.styleId" :styles="styles" />
        </div>

        <p v-if="localError || (trial.step === 'error' && trial.error)" class="error small">
          {{ localError || trial.error }}
        </p>

        <div v-if="trial.trialUsed" class="upsell inline">
          <p class="muted small">{{ t('trial.usedUpsell') }}</p>
          <router-link class="btn primary" :to="signupTarget">{{ t('trial.continueAccount') }}</router-link>
        </div>

        <button
          v-else
          class="btn primary wide"
          :disabled="trial.busy || eligibility === false"
        >
          {{ selectedStyle ? t('trial.generateIn', { style: selectedStyle.label }) : t('trial.generate') }}
        </button>

        <p class="muted small">{{ t('trial.privacy') }}</p>

        <p class="muted small legal-hint">
          {{ t('trial.consent') }}
          <router-link to="/legal/confidentialitate" target="_blank">{{ t('legal.privacy') }}</router-link>
          ·
          <router-link to="/legal/termeni" target="_blank">{{ t('legal.terms') }}</router-link>.
          {{ t('trial.consentAfter') }}
        </p>
      </div>
    </form>
  </section>
</template>
