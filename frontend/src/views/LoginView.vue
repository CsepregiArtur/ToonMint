<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

// Pagina de autentificare / creare cont.
const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const mode = ref('login')
const email = ref('')
const password = ref('')
const displayName = ref('')
const localError = ref('')

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

      <p class="muted small">
        Datele tale sunt procesate temporar: imaginile nu sunt păstrate pe server.
      </p>
    </section>
  </div>
</template>
