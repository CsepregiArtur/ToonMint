<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { t } from '../i18n'

// Pagina de autentificare: login, creare cont, confirmare email cu cod și resetare parolă.
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

// step: credentials (login/register) | verify | forgot | reset
const step = ref('credentials')
const mode = ref('login')
const demo = ref(null)
const demoBusy = ref('')
const email = ref('')
const pendingEmail = ref('')
const password = ref('')
const displayName = ref('')
const code = ref('')
const newPassword = ref('')
const localError = ref('')
const info = ref('')
// Consimțământul la termeni este obligatoriu la creare contului (OUG 34/2014 + GDPR art. 13).
const termsAccepted = ref(false)
const cooldown = ref(0)
let cooldownTimer = null

const busy = computed(() => auth.loading)

// Intră instant cu un cont demo (doar când backendul rulează în dezvoltare).
async function useDemo(role) {
  localError.value = ''
  demoBusy.value = role
  try {
    await auth.demoLogin(role)
    await router.push(route.query.redirect || (role === 'ADMIN' ? '/admin' : '/studio'))
  } catch (error) {
    localError.value = error.message
  } finally {
    demoBusy.value = ''
  }
}

// La montare: pornim în modul cerut de link și aflăm dacă login-ul demo e disponibil.
onMounted(async () => {
  if (route.query.mode === 'register') {
    mode.value = 'register'
  }
  if (route.query.redirect) {
    demo.value = null
  }
  demo.value = await auth.demoAvailable()
})

onUnmounted(stopCooldown)

// Pornește numărătoarea pentru retrimiterea codului.
function startCooldown(seconds) {
  stopCooldown()
  cooldown.value = Math.max(0, Math.ceil(seconds || 0))
  if (cooldown.value === 0) {
    return
  }
  cooldownTimer = setInterval(() => {
    cooldown.value -= 1
    if (cooldown.value <= 0) {
      stopCooldown()
    }
  }, 1000)
}

function stopCooldown() {
  if (cooldownTimer) {
    clearInterval(cooldownTimer)
    cooldownTimer = null
  }
  cooldown.value = 0
}

// Comută între formularul de login și cel de înregistrare.
function switchMode(next) {
  mode.value = next
  localError.value = ''
  info.value = ''
  auth.error = ''
}

// Revine la formularul principal, păstrând emailul introdus.
function backToCredentials() {
  step.value = 'credentials'
  code.value = ''
  newPassword.value = ''
  localError.value = ''
  info.value = ''
  auth.error = ''
  stopCooldown()
}

// Validează câmpurile, apelează store-ul și redirecționează după succes.
async function submitCredentials() {
  localError.value = ''
  info.value = ''
  if (!email.value || !password.value) {
    localError.value = t('auth.needFields')
    return
  }
  if (mode.value === 'register' && password.value.length < 8) {
    localError.value = t('auth.passShort')
    return
  }
  if (mode.value === 'register' && !termsAccepted.value) {
    localError.value = t('auth.needTerms')
    return
  }
  try {
    if (mode.value === 'login') {
      await auth.login(email.value, password.value)
      await router.push(route.query.redirect || '/studio')
      return
    }
    const pending = await auth.register(email.value, password.value, displayName.value)
    pendingEmail.value = pending?.email || email.value
    code.value = ''
    step.value = 'verify'
    info.value = `${t('auth.verifySent', { email: pendingEmail.value })} ${t('auth.verifyHint')}`
    startCooldown(pending?.resendAvailableInSeconds)
  } catch (error) {
    if (error.code === 'EMAIL_NOT_VERIFIED') {
      pendingEmail.value = email.value
      code.value = ''
      step.value = 'verify'
      info.value = t('auth.verifyHint')
      startCooldown(0)
      return
    }
    localError.value = error.message
  }
}

// Confirmă adresa de email cu codul primit.
async function submitVerification() {
  localError.value = ''
  if (code.value.trim().length !== 6) {
    localError.value = t('auth.needCode')
    return
  }
  try {
    await auth.verifyEmail(pendingEmail.value, code.value.trim())
    await router.push(route.query.redirect || '/studio')
  } catch (error) {
    localError.value = error.message
  }
}

// Retrimite codul de verificare a emailului.
async function resendVerification() {
  localError.value = ''
  try {
    const result = await auth.resendVerification(pendingEmail.value)
    info.value = t('auth.codeSentAgain')
    startCooldown(result?.resendAvailableInSeconds)
  } catch (error) {
    localError.value = error.message
    if (error.code === 'CODE_COOLDOWN') {
      startCooldown(error.response?.data?.details?.retryAfterSeconds)
    }
  }
}

// Pornește fluxul de resetare a parolei.
function startForgot() {
  step.value = 'forgot'
  code.value = ''
  newPassword.value = ''
  localError.value = ''
  info.value = ''
  auth.error = ''
  stopCooldown()
}

// Cere codul de resetare pentru adresa introdusă.
async function requestResetCode() {
  localError.value = ''
  if (!email.value) {
    localError.value = t('auth.needEmail')
    return
  }
  try {
    const result = await auth.forgotPassword(email.value)
    pendingEmail.value = email.value
    code.value = ''
    newPassword.value = ''
    step.value = 'reset'
    info.value = t('auth.resetSent', { email: email.value })
    startCooldown(result?.resendAvailableInSeconds)
  } catch (error) {
    localError.value = error.message
  }
}

// Setează parola nouă și intră în cont.
async function submitReset() {
  localError.value = ''
  if (code.value.trim().length !== 6) {
    localError.value = t('auth.needCode')
    return
  }
  if (newPassword.value.length < 8) {
    localError.value = t('auth.passShort')
    return
  }
  try {
    await auth.resetPassword(pendingEmail.value, code.value.trim(), newPassword.value)
    await router.push(route.query.redirect || '/studio')
  } catch (error) {
    localError.value = error.message
  }
}
</script>

<template>
  <div class="auth-shell">
    <section class="card auth-card">
      <div class="brand">
        <img class="brand-mark" src="/toonmint-mark.svg" alt="" />
        <div>
          <h1>ToonMint</h1>
          <p class="muted">{{ t('auth.tagline') }}</p>
        </div>
      </div>

      <div v-if="step === 'credentials'" class="tabs">
        <button :class="{ active: mode === 'login' }" @click="switchMode('login')">{{ t('auth.tabLogin') }}</button>
        <button :class="{ active: mode === 'register' }" @click="switchMode('register')">{{ t('auth.tabRegister') }}</button>
      </div>

      <form v-if="step === 'credentials'" class="stack-sm" @submit.prevent="submitCredentials">
        <label v-if="mode === 'register'" class="field">
          <span>{{ t('auth.name') }}</span>
          <input v-model="displayName" maxlength="100" :placeholder="t('auth.namePlaceholder')" />
        </label>
        <label class="field">
          <span>{{ t('auth.email') }}</span>
          <input v-model="email" type="email" autocomplete="email" required />
        </label>
        <label class="field">
          <span>{{ t('auth.password') }}</span>
          <input
            v-model="password"
            type="password"
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
            required
          />
        </label>

        <p v-if="localError || auth.error" class="error">{{ localError || auth.error }}</p>

        <label v-if="mode === 'register'" class="legal-check">
          <input v-model="termsAccepted" type="checkbox" />
          <span>
            {{ t('auth.consentRead') }}
            <router-link to="/legal/termeni" target="_blank">{{ t('legal.terms') }}</router-link>
            {{ t('auth.consentAnd') }}
            <router-link to="/legal/confidentialitate" target="_blank">{{ t('legal.privacy') }}</router-link>.
            {{ t('auth.consentAge') }}
          </span>
        </label>

        <button class="btn primary wide" :disabled="busy">
          {{ busy ? t('auth.processing') : mode === 'login' ? t('auth.login') : t('auth.register') }}
        </button>

        <button v-if="mode === 'login'" type="button" class="link-btn" @click="startForgot">
          {{ t('auth.forgot') }}
        </button>
      </form>

      <form v-else-if="step === 'verify'" class="stack-sm" @submit.prevent="submitVerification">
        <p class="muted small">
          {{ t('auth.verifySent', { email: pendingEmail }) }}
        </p>
        <label class="field">
          <span>{{ t('auth.codeLabel') }}</span>
          <input
            v-model="code"
            inputmode="numeric"
            maxlength="6"
            autocomplete="one-time-code"
            placeholder="123456"
            required
          />
        </label>

        <p v-if="info" class="muted small">{{ info }}</p>
        <p v-if="localError || auth.error" class="error">{{ localError || auth.error }}</p>

        <button class="btn primary wide" :disabled="busy">
          {{ busy ? t('auth.verifying') : t('auth.verifyCta') }}
        </button>
        <button type="button" class="btn ghost wide" :disabled="busy || cooldown > 0" @click="resendVerification">
          {{ cooldown > 0 ? t('auth.resendIn', { seconds: cooldown }) : t('auth.resend') }}
        </button>
        <button type="button" class="link-btn" @click="backToCredentials">{{ t('auth.backToLogin') }}</button>
      </form>

      <form v-else-if="step === 'forgot'" class="stack-sm" @submit.prevent="requestResetCode">
        <p class="muted small">{{ t('auth.forgotHint') }}</p>
        <label class="field">
          <span>{{ t('auth.email') }}</span>
          <input v-model="email" type="email" autocomplete="email" required />
        </label>

        <p v-if="localError || auth.error" class="error">{{ localError || auth.error }}</p>

        <button class="btn primary wide" :disabled="busy">
          {{ busy ? t('auth.sending') : t('auth.sendCode') }}
        </button>
        <button type="button" class="link-btn" @click="backToCredentials">{{ t('auth.backToLogin') }}</button>
      </form>

      <form v-else class="stack-sm" @submit.prevent="submitReset">
        <p class="muted small">{{ t('auth.resetSent', { email: pendingEmail }) }}</p>
        <label class="field">
          <span>{{ t('auth.resetLabel') }}</span>
          <input
            v-model="code"
            inputmode="numeric"
            maxlength="6"
            autocomplete="one-time-code"
            placeholder="123456"
            required
          />
        </label>
        <label class="field">
          <span>{{ t('auth.newPassword') }}</span>
          <input v-model="newPassword" type="password" autocomplete="new-password" minlength="8" required />
        </label>

        <p v-if="info" class="muted small">{{ info }}</p>
        <p v-if="localError || auth.error" class="error">{{ localError || auth.error }}</p>

        <button class="btn primary wide" :disabled="busy">
          {{ busy ? t('auth.saving') : t('auth.resetCta') }}
        </button>
        <button type="button" class="btn ghost wide" :disabled="busy || cooldown > 0" @click="requestResetCode">
          {{ cooldown > 0 ? t('auth.resendIn', { seconds: cooldown }) : t('auth.resend') }}
        </button>
        <button type="button" class="link-btn" @click="startForgot">{{ t('auth.changeEmail') }}</button>
      </form>

      <div v-if="demo && step === 'credentials'" class="demo-block">
        <div class="divider"><span>{{ t('auth.demoDivider') }}</span></div>
        <div class="demo-buttons">
          <button class="btn ghost" :disabled="Boolean(demoBusy)" @click="useDemo('USER')">
            {{ demoBusy === 'USER' ? t('auth.demoOpening') : t('auth.demoUser') }}
          </button>
          <button class="btn ghost" :disabled="Boolean(demoBusy)" @click="useDemo('ADMIN')">
            {{ demoBusy === 'ADMIN' ? t('auth.demoOpening') : t('auth.demoAdmin') }}
          </button>
        </div>
        <p class="muted small">{{ t('auth.demoNote') }}</p>
      </div>

      <p class="muted small">{{ t('auth.privacyNote') }}</p>
    </section>
  </div>
</template>
