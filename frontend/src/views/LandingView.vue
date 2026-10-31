<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import BeforeAfterSlider from '../components/BeforeAfterSlider.vue'
import TrialGenerator from '../components/TrialGenerator.vue'
import http from '../api/client'
import { useTrialStore } from '../stores/trial'

// Prima pagină: demonstrație „înainte/după” cu slider și generarea gratuită.
const auth = useAuthStore()

// Utilizatorii autentificați au un drum direct către studio.
const loggedIn = computed(() => auth.isAuthenticated)

// Catalogul de stiluri, fiecare cu imaginea de exemplu (generată cu Gemini).
const styles = ref([])
const trial = useTrialStore()

// Previzualizările lipsă (încă negenerate) sunt afișate ca plăci cu eticheta stilului.
const missingPreviews = ref(new Set())

// Hero-ul compară fotografia reală cu o variantă toon generată de AI; până când
// acea imagine există, arătăm doar fotografia, ca să nu inducă în eroare.
const heroAfterReady = ref(false)

// Marchează o previzualizare care nu poate fi încărcată.
function markPreviewMissing(id) {
  missingPreviews.value = new Set([...missingPreviews.value, id])
}

// Alege un stil din galerie și duce vizitatorul la formularul de generare.
function chooseStyle(id) {
  trial.selectStyle(id)
  document.getElementById('incearca')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

// Încarcă stilurile publice și verifică dacă există imaginea „după” pentru hero.
onMounted(async () => {
  try {
    const { data } = await http.get('/styles')
    styles.value = data
  } catch (error) {
    styles.value = []
  }

  const probe = new Image()
  probe.onload = () => {
    heroAfterReady.value = true
  }
  probe.onerror = () => {
    heroAfterReady.value = false
  }
  probe.src = '/demo/after.jpg'
})

// Pașii explicați sub demonstrație.
const steps = [
  { title: 'Încarci o fotografie', text: 'Portret, animal sau peisaj — orice poză funcționează.' },
  { title: 'Alegi un stil', text: 'Peste 20 de stiluri: anime, manga, watercolor, cyberpunk și altele.' },
  { title: 'Descarci rezultatul', text: 'Primești imaginea în câteva secunde, gata de distribuit.' }
]
</script>

<template>
  <div class="landing">
    <section class="hero">
      <div class="hero-copy">
        <span class="eyebrow">Photo → Toon</span>
        <h1>Transformă orice fotografie în artă toon</h1>
        <p class="hero-sub">
          Încarci o poză, alegi un stil și primești varianta desenată în câteva secunde.
          Prima imagine este gratuită — fără cont, doar cu adresa de email.
        </p>

        <div class="hero-actions">
          <a class="btn primary" href="#incearca">Încearcă gratuit</a>
          <template v-if="loggedIn">
            <router-link class="btn ghost" to="/studio">Continuă în studio</router-link>
          </template>
          <template v-else>
            <router-link class="btn ghost" to="/login">Autentificare</router-link>
          </template>
        </div>

        <ul class="hero-facts">
          <li>1 imagine gratuită per email</li>
          <li>Fără stocare permanentă a fotografiilor</li>
          <li>Abonament de la 4.99 $/săptămână</li>
        </ul>
      </div>

      <div class="hero-visual">
        <BeforeAfterSlider
          v-if="heroAfterReady"
          before="/demo/before.jpg"
          after="/demo/after.jpg"
          before-label="Fotografie"
          after-label="Toon"
          :initial="52"
        />
        <div v-else class="hero-photo">
          <img src="/demo/before.jpg" alt="Fotografie de exemplu" />
          <span class="hero-photo-badge">Fotografie</span>
        </div>
        <p class="slider-hint muted small">
          {{ heroAfterReady
            ? 'Trage de bară pentru a compara fotografia cu varianta toon.'
            : 'Varianta toon apare aici după prima generare cu Gemini.' }}
        </p>
      </div>
    </section>

    <TrialGenerator />

    <section v-if="styles.length" class="card styles-showcase">
      <header class="card-header">
        <div>
          <h2>Peste {{ styles.length }} de stiluri artistice</h2>
          <p class="muted">Alege atmosfera potrivită pentru fiecare fotografie.</p>
        </div>
        <a class="btn ghost" href="#incearca">Alege un stil</a>
      </header>
      <div class="style-gallery">
        <button
          v-for="style in styles"
          :key="style.id"
          type="button"
          class="style-card"
          :class="{ active: style.id === trial.styleId, pending: missingPreviews.has(style.id) }"
          :title="`Generează în stilul ${style.label}`"
          @click="chooseStyle(style.id)"
        >
          <img
            v-if="style.previewUrl && !missingPreviews.has(style.id)"
            :src="style.previewUrl"
            :alt="`Exemplu ${style.label}`"
            loading="lazy"
            @error="markPreviewMissing(style.id)"
          />
          <span v-else class="style-card-placeholder">{{ style.label }}</span>
          <span class="style-card-label">{{ style.label }}</span>
        </button>
      </div>
    </section>

    <section class="steps">
      <article v-for="(step, index) in steps" :key="step.title" class="step">
        <span class="step-index">{{ index + 1 }}</span>
        <h3>{{ step.title }}</h3>
        <p class="muted small">{{ step.text }}</p>
      </article>
    </section>

    <section class="pricing-teaser card">
      <div>
        <h2>După imaginea gratuită</h2>
        <p class="muted">
          Abonamentul săptămânal (4.99 $) include 50 de generări, cel anual (24.99 $) — 300.
          Dacă rămâi fără credite, poți cumpăra 20 de generări pentru 1.99 $.
        </p>
      </div>
      <router-link class="btn primary" :to="loggedIn ? '/credits' : '/login?mode=register'">
        {{ loggedIn ? 'Vezi abonamentele' : 'Creează cont' }}
      </router-link>
    </section>
  </div>
</template>
