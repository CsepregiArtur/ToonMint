<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

// Pagina de autentificare / creare cont.
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref('login')
const demo = ref(null)
const demoBusy = ref('')
const email = ref('')
const password = ref('')
const displayName = ref('')
const localError = ref('')

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

// Comută între formularul de login și cel de înregistrare.
function switchMode(next) {
  mode.value = next
  localError.value = ''
  auth.error = ''
}

// Validează câmpurile, apelează store-ul și redirecționează după succes.
async function submit() {
  localError.value = ''
  if (!email.value || !password.value) {
    localError.value = 'Completează emailul și parola.'
    return
  }
  if (mode.value === 'register' && password.value.length < 8) {
    localError.value = 'Parola trebuie să aibă cel puțin 8 caractere.'
    return
  }
  try {
    if (mode.value === 'login') {
      await auth.login(email.value, password.value)
    } else {
      await auth.register(email.value, password.value, displayName.value)
    }
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
        <span class="brand-mark">TM</span>
        <div>
          <h1>ToonMint</h1>
          <p class="muted">Fotografiile tale, transformate în artă.</p>
        </div>
      </div>

      <div class="tabs">
        <button :class="{ active: mode === 'login' }" @click="switchMode('login')">Autentificare</button>
        <button :class="{ active: mode === 'register' }" @click="switchMode('register')">Cont nou</button>
      </div>

      <form class="stack-sm" @submit.prevent="submit">
        <label v-if="mode === 'register'" class="field">
          <span>Nume afișat</span>
          <input v-model="displayName" maxlength="100" placeholder="Cum te numim?" />
        </label>
        <label class="field">
          <span>Email</span>
          <input v-model="email" type="email" autocomplete="email" required />
        </label>
        <label class="field">
          <span>Parolă</span>
          <input
            v-model="password"
            type="password"
            :autocomplete="mode === 'login' ? 'current-password' : 'new-password'"
            required
          />
        </label>

        <p v-if="localError || auth.error" class="error">{{ localError || auth.error }}</p>

        <button class="btn primary wide" :disabled="auth.loading">
          {{ auth.loading ? 'Se procesează…' : mode === 'login' ? 'Intră în cont' : 'Creează contul' }}
        </button>
      </form>

      <div v-if="demo" class="demo-block">
        <div class="divider"><span>sau intră instant în dezvoltare</span></div>
        <div class="demo-buttons">
          <button class="btn ghost" :disabled="Boolean(demoBusy)" @click="useDemo('USER')">
            {{ demoBusy === 'USER' ? 'Se deschide…' : 'Cont demo' }}
          </button>
          <button class="btn ghost" :disabled="Boolean(demoBusy)" @click="useDemo('ADMIN')">
            {{ demoBusy === 'ADMIN' ? 'Se deschide…' : 'Operator demo' }}
          </button>
        </div>
        <p class="muted small">
          Login demo activat de server (profilul <code>dev</code>). Fără parolă, doar pentru testare.
        </p>
      </div>

      <p class="muted small">
        Datele tale sunt procesate temporar: imaginile nu sunt păstrate pe server.
      </p>
    </section>
  </div>
</template>
