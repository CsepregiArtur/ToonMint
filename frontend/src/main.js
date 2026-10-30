import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'
import './assets/styles.css'

// Punctul de intrare al aplicației Vue: montează Pinia (starea globală) și router-ul.
createApp(App).use(createPinia()).use(router).mount('#app')
