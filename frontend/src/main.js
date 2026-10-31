import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/styles.css'
import { bootstrapConsentDefaults } from './lib/analytics'
import { initI18n } from './i18n'

// Punctul de intrare al aplicației Vue: montează Pinia (starea globală) și router-ul.
// Limba se alege înainte de randare (URL → preferință → browser → fus orar),
// iar Consent Mode pornește pe „denied” înainte ca vreo etichetă să fie încărcată.
initI18n()
bootstrapConsentDefaults()

createApp(App).use(createPinia()).use(router).mount('#app')
