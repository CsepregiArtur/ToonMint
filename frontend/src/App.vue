<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

// Shell-ul aplicației: bara de navigare, soldul de credite și ieșirea din cont.
const auth = useAuthStore()
const router = useRouter()

// Afișăm bara de navigare doar după autentificare.
const isLoggedIn = computed(() => auth.isAuthenticated)

// Deconectează utilizatorul și îl trimite la pagina de login.
function logout() {
  auth.logout()
  router.push('/login')
}

// Încărcăm profilul o singură dată la pornirea aplicației.
onMounted(() => {
  if (auth.token) {
    auth.loadProfile()
  }
})
</script>

<template>
  <div class="app-shell">
    <header v-if="isLoggedIn" class="topbar">
      <router-link to="/studio" class="brand small">
        <span class="brand-mark">TM</span>
        <span>ToonMint</span>
      </router-link>

      <nav class="nav">
        <router-link to="/studio">Studio</router-link>
        <router-link to="/credits">Credite</router-link>
        <router-link v-if="auth.isAdmin" to="/admin">Admin</router-link>
      </nav>

      <div class="topbar-right">
        <span class="credit-pill">{{ auth.credits }} credite</span>
        <span class="user-email">{{ auth.user?.email }}</span>
        <button class="btn ghost small" @click="logout">Ieși</button>
      </div>
    </header>

    <main class="content">
      <router-view />
    </main>

    <footer class="footer muted small">
      ToonMint · imagini procesate temporar, fără stocare permanentă · plăți prin RevenueCat
    </footer>
  </div>
</template>
