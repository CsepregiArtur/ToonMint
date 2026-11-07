<script setup>
import { computed, onMounted, onUnmounted, watch } from 'vue'
import { useConsentStore } from '../stores/consent'
import { SITE } from '../legal/site'
import { formatDate, t } from '../i18n'

// Bannerul de consimțământ + panoul de preferințe.
// Reguli respectate (ePrivacy / Legea 506/2004):
//   - refuzul e la fel de simplu ca acceptul („Doar esențiale”);
//   - nimic opțional nu se încarcă înainte de apăsarea unui buton;
//   - alegerea poate fi schimbată oricând (link în subsol).
const consent = useConsentStore()

// Afișăm bannerul doar după ce am citit starea salvată, ca să nu clipească
// pentru vizitatorii care au ales deja.
const showBar = computed(() => consent.ready && !consent.decided && !consent.settingsOpen)

function onKeydown(event) {
  if (event.key === 'Escape' && consent.settingsOpen) {
    consent.closeSettings()
  }
}

onMounted(() => {
  consent.init()
  window.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
})

// Blochează scroll-ul paginii cât timp panoul de setări este deschis.
watch(
  () => consent.settingsOpen,
  (open) => {
    document.body.style.overflow = open ? 'hidden' : ''
  }
)
</script>

<template>
  <div v-if="showBar" class="cookie-notice" role="region" :aria-label="t('consent.regionLabel')">
    <p>
      🍪 {{ t('consent.text') }}
      <router-link to="/legal/cookies">{{ t('legal.cookies') }}</router-link> ·
      <router-link to="/legal/confidentialitate">{{ t('legal.privacy') }}</router-link>
    </p>
    <div class="cookie-actions">
      <button type="button" class="btn ghost small" @click="consent.openSettings()">{{ t('consent.customize') }}</button>
      <button type="button" class="btn ghost small" @click="consent.rejectAll()">{{ t('consent.reject') }}</button>
      <button type="button" class="btn primary small" @click="consent.acceptAll()">{{ t('consent.accept') }}</button>
    </div>
  </div>

  <div v-if="consent.settingsOpen" class="cookie-overlay" @click.self="consent.closeSettings()">
    <div class="cookie-modal" role="dialog" aria-modal="true" aria-labelledby="cookie-modal-title">
      <header>
        <h2 id="cookie-modal-title">{{ t('consent.modal.title') }}</h2>
        <button type="button" class="cookie-close" :aria-label="t('common.close')" @click="consent.closeSettings()">✕</button>
      </header>

      <p class="muted small">
        {{ t('consent.modal.desc', { version: formatDate(SITE.docVersion) }) }}
        <router-link to="/legal/cookies" @click="consent.closeSettings()">{{ t('consent.modal.policy') }}</router-link>.
      </p>

      <div class="cookie-row">
        <div>
          <strong>{{ t('consent.modal.necessary') }}</strong>
          <p class="muted small">{{ t('consent.modal.necessaryText') }}</p>
        </div>
        <span class="cookie-badge">{{ t('consent.modal.always') }}</span>
      </div>

      <label class="cookie-row">
        <div>
          <strong>{{ t('consent.modal.analytics') }}</strong>
          <p class="muted small">{{ t('consent.modal.analyticsText') }}</p>
        </div>
        <input v-model="consent.draft.analytics" type="checkbox" class="cookie-switch" />
      </label>

      <label class="cookie-row">
        <div>
          <strong>{{ t('consent.modal.ads') }}</strong>
          <p class="muted small">{{ t('consent.modal.adsText') }}</p>
        </div>
        <input v-model="consent.draft.ads" type="checkbox" class="cookie-switch" />
      </label>

      <footer class="cookie-modal-actions">
        <button type="button" class="btn ghost small" @click="consent.withdraw()">{{ t('consent.modal.withdraw') }}</button>
        <button type="button" class="btn ghost small" @click="consent.rejectAll()">{{ t('consent.reject') }}</button>
        <button type="button" class="btn primary small" @click="consent.saveDraft()">{{ t('consent.modal.save') }}</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.cookie-notice {
  position: fixed;
  inset: auto 0 0 0;
  z-index: 60;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: rgba(11, 19, 43, 0.96);
  border-top: 1px solid var(--border);
  backdrop-filter: blur(14px);
  font-size: 0.9rem;
  color: #f4f1ff;
}

.cookie-notice p {
  margin: 0;
  max-width: 78ch;
  color: inherit;
}

.cookie-notice a {
  color: #ffcf9a;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.cookie-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cookie-overlay {
  position: fixed;
  inset: 0;
  z-index: 70;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(5, 4, 12, 0.72);
  backdrop-filter: blur(6px);
}

.cookie-modal {
  width: min(620px, 100%);
  max-height: 88vh;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 22px;
  border: 1px solid var(--border);
  border-radius: var(--radius);
  background: #151228;
  color: #f4f1ff;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.45);
}

.cookie-modal .muted {
  color: #b9b4cb;
}

.cookie-modal a {
  color: #ffcf9a;
  text-decoration: underline;
  text-underline-offset: 3px;
}

.cookie-modal header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.cookie-close {
  border: none;
  background: transparent;
  color: var(--muted);
  font-size: 1.1rem;
  cursor: pointer;
}

.cookie-row {
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 14px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.04);
}

.cookie-row code {
  padding: 1px 5px;
  border-radius: 5px;
  background: rgba(255, 255, 255, 0.08);
  font-size: 0.85em;
}

.cookie-badge {
  flex: none;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(74, 222, 128, 0.15);
  color: var(--success);
  font-size: 0.75rem;
  white-space: nowrap;
}

.cookie-switch {
  flex: none;
  width: 42px;
  height: 24px;
  accent-color: var(--primary);
  cursor: pointer;
}

.cookie-modal-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}
</style>
