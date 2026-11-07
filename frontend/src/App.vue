<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useConsentStore } from './stores/consent'
import { LEGAL_LINKS, SITE } from './legal/site'
import CookieNotice from './components/CookieNotice.vue'
import LanguageSwitcher from './components/LanguageSwitcher.vue'
import { t } from './i18n'
import { version as appVersion } from '../package.json'

// Shell-ul aplicației: bara de navigare, soldul de credite și ieșirea din cont.
const auth = useAuthStore()
const router = useRouter()
const consent = useConsentStore()

// Afișăm bara de navigare doar după autentificare.
const isLoggedIn = computed(() => auth.isAuthenticated)
// Pe prima pagină (publică) arătăm doar brandul și linkul de autentificare.
const route = useRoute()
const isLanding = computed(() => route.name === 'landing')

// Deconectează utilizatorul și îl trimite la pagina de login.
function logout() {
  auth.logout()
  router.push('/login')
}

// Încărcăm profilul o singură dată la pornirea aplicației.
onMounted(() => {
  // Reia sesiunea: tokenul de acces expiră în 24 de ore, dar tokenul de refresh
  // ține contul deschis între lansările aplicației.
  auth.restoreSession()
})
</script>

<template>
  <div class="app-shell">
    <header v-if="isLoggedIn" class="topbar">
      <router-link to="/studio" class="brand small">
        <img class="brand-mark" src="/toonmint-mark.svg" alt="" />
        <span>ToonMint</span>
      </router-link>

      <nav class="nav">
        <router-link to="/studio">{{ t('nav.studio') }}</router-link>
        <router-link to="/credits">{{ t('nav.credits') }}</router-link>
        <router-link to="/cont">{{ t('nav.account') }}</router-link>
        <router-link v-if="auth.isAdmin" to="/admin">{{ t('nav.admin') }}</router-link>
      </nav>

      <div class="topbar-right">
        <span class="credit-pill">{{ t('common.credits', { count: auth.credits }) }}</span>
        <span class="user-email">{{ auth.user?.email }}</span>
        <LanguageSwitcher compact />
        <button class="btn ghost small" @click="logout">{{ t('nav.logout') }}</button>
      </div>
    </header>

    <header v-else-if="isLanding" class="topbar public">
      <router-link to="/" class="brand small">
        <img class="brand-mark" src="/toonmint-mark.svg" alt="" />
        <span>ToonMint</span>
      </router-link>
      <div class="topbar-right">
        <LanguageSwitcher compact />
        <router-link class="btn ghost small" to="/login">{{ t('nav.login') }}</router-link>
      </div>
    </header>

    <main class="content">
      <router-view />
    </main>

    <footer class="footer muted small">
      <p class="footer-brand"><img src="/toonmint-mark.svg" alt="" />{{ t('footer.tagline') }}</p>

      <nav class="legal-links" :aria-label="t('footer.legalNav')">
        <router-link v-for="link in LEGAL_LINKS" :key="link.to" :to="link.to">{{ t(link.key) }}</router-link>
        <button type="button" class="link-btn" @click="consent.openSettings()">
          {{ t('footer.cookieSettings') }}
        </button>
        <LanguageSwitcher compact />
      </nav>

      <p class="legal-entity">
        {{ SITE.entity.name }}<template v-if="SITE.entity.cui"> · CUI {{ SITE.entity.cui }}</template><template
          v-if="SITE.entity.regCom"
        > · {{ SITE.entity.regCom }}</template> · {{ SITE.entity.address }} ·
        <a :href="`mailto:${SITE.entity.email}`">{{ SITE.entity.email }}</a>
      </p>
      <p class="legal-entity">
        <a :href="SITE.anpc.sal" target="_blank" rel="noopener noreferrer">{{ t('footer.anpc') }}</a> ·
        <a :href="SITE.anpc.sol" target="_blank" rel="noopener noreferrer">{{ t('footer.sol') }}</a>
      </p>
      <p class="app-version">ToonMint v{{ appVersion }}</p>
    </footer>

    <CookieNotice />
  </div>
</template>
