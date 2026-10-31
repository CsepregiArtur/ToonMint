import { createRouter, createWebHistory } from 'vue-router'
import LoginView from './views/LoginView.vue'
import LandingView from './views/LandingView.vue'
import StudioView from './views/StudioView.vue'
import CreditsView from './views/CreditsView.vue'
import AdminView from './views/AdminView.vue'
import { useAuthStore } from './stores/auth'

// Rutele aplicației. Prima pagină este publică (demonstrație + generare gratuită),
// restul cer autentificare, iar /admin cere rol de operator.
const routes = [
  { path: '/', name: 'landing', component: LandingView, meta: { public: true } },
  { path: '/login', name: 'login', component: LoginView, meta: { public: true } },
  { path: '/studio', name: 'studio', component: StudioView },
  { path: '/credits', name: 'credits', component: CreditsView },
  { path: '/admin', name: 'admin', component: AdminView, meta: { admin: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// Gardă de navigare: blochează accesul fără autentificare și accesul neautorizat la zona de admin.
router.beforeEach(async (to) => {
  const auth = useAuthStore()

  if (to.meta.public) {
    return true
  }
  if (!auth.token) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (!auth.user) {
    await auth.loadProfile()
  }
  if (to.meta.admin && !auth.isAdmin) {
    return { name: 'studio' }
  }
  return true
})

export default router
