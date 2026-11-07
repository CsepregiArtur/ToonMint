<script setup>
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '../stores/auth'
import BeforeAfterSlider from '../components/BeforeAfterSlider.vue'
import TrialGenerator from '../components/TrialGenerator.vue'
import http from '../api/client'
import { useTrialStore } from '../stores/trial'
import { t } from '../i18n'

// Prima pagină: demonstrație „înainte/după” cu slider și generarea gratuită.
const auth = useAuthStore()

// Utilizatorii autentificați au un drum direct către studio.
const loggedIn = computed(() => auth.isAuthenticated)

// Catalogul de stiluri, fiecare cu imaginea de exemplu (generată cu Gemini).
const styles = ref([])
const trial = useTrialStore()

// Previzualizările lipsă (încă negenerate) sunt afișate ca plăci cu eticheta stilului.
const missingPreviews = ref(new Set())
const pricing = ref({ plans: [], packs: [] })

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

  try {
    const { data } = await http.get('/public/billing/catalog')
    pricing.value = data
  } catch (error) {
    pricing.value = { plans: [], packs: [] }
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

// Pașii explicați sub demonstrație (recalculați când se schimbă limba).
const steps = computed(() => [
  { title: t('landing.steps.1t'), text: t('landing.steps.1d') },
  { title: t('landing.steps.2t'), text: t('landing.steps.2d') },
  { title: t('landing.steps.3t'), text: t('landing.steps.3d') }
])
</script>

<template>
  <div class="landing">
    <section class="hero">
      <div class="hero-copy">
        <span class="eyebrow">{{ t('landing.eyebrow') }}</span>
        <h1>{{ t('landing.title') }}</h1>
        <p class="hero-sub">{{ t('landing.subtitle') }}</p>

        <div class="hero-actions">
          <a class="btn primary" href="#incearca">{{ t('landing.try') }}</a>
          <template v-if="loggedIn">
            <router-link class="btn ghost" to="/studio">{{ t('landing.continue') }}</router-link>
          </template>
          <template v-else>
            <router-link class="btn ghost" to="/login">{{ t('nav.login') }}</router-link>
          </template>
        </div>

        <ul class="hero-facts">
          <li>{{ t('landing.fact.free') }}</li>
          <li>{{ t('landing.fact.noStore') }}</li>
          <li>{{ t('landing.fact.preview') }}</li>
        </ul>
      </div>

      <div class="hero-visual">
        <BeforeAfterSlider
          v-if="heroAfterReady"
          before="/demo/before.jpg"
          after="/demo/after.jpg"
          :before-label="t('trial.before')"
          :after-label="t('trial.after')"
          :initial="52"
          class="hero-demo"
        />
        <div v-else class="hero-photo">
          <img src="/demo/before.jpg" alt="ToonMint" />
          <span class="hero-photo-badge">{{ t('landing.badge.photo') }}</span>
        </div>
        <p class="slider-hint muted small">
          {{ heroAfterReady ? t('landing.slider.ready') : t('landing.slider.pending') }}
        </p>
      </div>
    </section>

    <TrialGenerator />

    <section v-if="styles.length" id="stiluri" class="card styles-showcase">
      <header class="card-header">
        <div>
          <h2>{{ t('landing.gallery.title') }}</h2>
          <p class="muted">{{ t('landing.gallery.subtitle', { count: styles.length }) }}</p>
        </div>
        <a class="btn ghost" href="#incearca">{{ t('landing.gallery.cta') }}</a>
      </header>
      <div class="style-gallery">
        <button
          v-for="style in styles"
          :key="style.id"
          type="button"
          class="style-card"
          :class="{ active: style.id === trial.styleId, pending: missingPreviews.has(style.id) }"
          :title="t('landing.gallery.try', { style: style.label })"
            :aria-label="t('landing.gallery.try', { style: style.label })"
          @click="chooseStyle(style.id)"
        >
          <img
            v-if="style.previewUrl && !missingPreviews.has(style.id)"
            :src="style.previewUrl"
            :alt="style.label"
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

    <section class="pricing-teaser card pricing-section">
      <div>
        <h2>{{ t('landing.pricing.title') }}</h2>
        <p class="muted">{{ t('landing.pricing.text') }}</p>
      </div>
      <div v-if="pricing.plans.length || pricing.packs.length" class="public-pricing-grid">
        <article v-for="product in [...pricing.plans, ...pricing.packs]" :key="product.id" class="public-price-tile">
          <span class="muted small">
            {{ pricing.plans.some((plan) => plan.id === product.id)
              ? t('landing.pricing.subscription')
              : t('landing.pricing.pack') }}
          </span>
          <strong>
            {{ product.kind === 'SUBSCRIPTION'
              ? (product.subscriptionType === 'WEEKLY' ? t('landing.pricing.weekly') : t('landing.pricing.yearly'))
              : t('landing.pricing.extraCredits', { count: product.credits }) }}
          </strong>
          <span class="public-price">{{ product.price }}</span>
          <span class="muted small">{{ t('landing.pricing.line', { count: product.credits }) }}</span>
        </article>
      </div>
      <p v-else class="muted small">{{ t('landing.pricing.unavailable') }}</p>
      <router-link class="btn primary" :to="loggedIn ? '/credits' : '/login?mode=register'">
        {{ loggedIn ? t('landing.pricing.ctaMember') : t('landing.pricing.ctaGuest') }}
      </router-link>
    </section>
  </div>
</template>
