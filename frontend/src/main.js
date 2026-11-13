import { createApp } from 'vue'
import './style.css'
import App from './App.vue'
import router from './router'
import { useAuth } from './stores/authStore'
import { useSettings } from './stores/settingsStore'

const app = createApp(App)
app.use(router)

// Restore the signed-in user and load restaurant settings before first render.
Promise.allSettled([useAuth().restore(), useSettings().load()]).finally(() => {
  app.mount('#app')
})
