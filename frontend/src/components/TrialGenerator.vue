<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useTrialStore } from '../stores/trial'
import BeforeAfterSlider from './BeforeAfterSlider.vue'
import StylePicker from './StylePicker.vue'
import http from '../api/client'

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
    localError.value = 'Sunt acceptate doar imagini PNG, JPEG sau WEBP.'
    return
  }
  if (selected.size > 10 * 1024 * 1024) {
    localError.value = 'Imaginea trebuie să aibă cel mult 10 MB.'
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
    localError.value = 'Introdu o adresă de email validă.'
    return
  }
  if (!file.value) {
    localError.value = 'Alege o fotografie.'
    return
  }
  if (!trial.styleId) {
    localError.value = 'Alege un stil.'
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
    localError.value = 'Stilurile nu au putut fi încărcate.'
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
        <h2>Încearcă gratuit, fără cont</h2>
        <p class="muted">
          Prima imagine este gratuită. Ai nevoie doar de o adresă de email, ca să știm că ai folosit-o.
        </p>
      </div>
      <span class="trial-pill">1 imagine gratuită</span>
    </header>

    <div v-if="trial.step === 'done'" class="result">
      <BeforeAfterSlider
        :before="trial.beforeUrl"
        :after="trial.afterUrl"
        before-label="Fotografia ta"
        after-label="Toon"
        :initial="55"
      />
      <div class="result-actions">
        <button class="btn primary" @click="trial.download()">Descarcă imaginea</button>
        <button class="btn ghost" @click="startOver">Altă fotografie</button>
      </div>
      <div class="upsell">
        <div>
          <strong>Vrei mai multe?</strong>
          <p class="muted small">
            Creează un cont și alege un abonament (50 de imagini/săptămână) sau cumpără credite extra.
          </p>
        </div>
        <div class="row">
          <router-link class="btn primary" to="/login?mode=register">Creează cont</router-link>
          <router-link class="btn ghost" to="/login">Am deja cont</router-link>
        </div>
      </div>
    </div>

    <div v-else-if="trial.busy" class="trial-busy">
      <div class="spinner" aria-hidden="true"></div>
      <p>
        {{ trial.step === 'uploading' ? 'Se trimite fotografia…' : 'Desenăm varianta toon…' }}
      </p>
      <p class="muted small">Durează de obicei câteva secunde.</p>
    </div>

    <form v-else class="trial-form" @submit.prevent="submit">
      <label class="dropzone compact" :class="{ filled: previewSrc }">
        <input
          ref="fileInput"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          hidden
          @change="onFileChange"
        />
        <img v-if="previewSrc" :src="previewSrc" alt="Previzualizare" />
        <div v-else class="dropzone-placeholder">
          <span class="dropzone-icon">＋</span>
          <strong>Alege fotografia</strong>
          <span class="muted small">PNG, JPEG sau WEBP · max 10 MB</span>
        </div>
      </label>

      <div class="trial-fields">
        <label class="field">
          <span>Adresa de email</span>
          <input
            v-model.trim="email"
            type="email"
            autocomplete="email"
            placeholder="tu@exemplu.com"
            required
            @blur="checkEmail"
          />
        </label>
        <p v-if="eligibility === false" class="error small">
          Această adresă a folosit deja imaginea gratuită.
          <router-link to="/login?mode=register">Creează un cont</router-link> pentru a continua.
        </p>

        <div class="field">
          <span>Stil ({{ styles.length }} disponibile)</span>
          <StylePicker v-model="trial.styleId" :styles="styles" />
        </div>

        <p v-if="localError || (trial.step === 'error' && trial.error)" class="error small">
          {{ localError || trial.error }}
        </p>

        <div v-if="trial.trialUsed" class="upsell inline">
          <p class="muted small">
            Generarea gratuită a fost folosită. Alege un abonament sau cumpără credite.
          </p>
          <router-link class="btn primary" to="/login?mode=register">Continuă cu un cont</router-link>
        </div>

        <button
          v-else
          class="btn primary wide"
          :disabled="trial.busy || eligibility === false"
        >
          Generează gratuit{{ selectedStyle ? ' · ' + selectedStyle.label : '' }}
        </button>

        <p class="muted small">
          Nu păstrăm fotografia: imaginea este procesată și ștearsă automat după 10 minute.
        </p>
      </div>
    </form>
  </section>
</template>
