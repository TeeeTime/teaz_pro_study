import './assets/main.css'
import '@teeetime/teaz-pro-ui/style.css'
import 'katex/dist/katex.min.css'

import { createApp } from 'vue'
import App from './App.vue'
import { router } from './router'
import { useAuth } from './auth'

// Mount only once logged in; with Keycloak, init() may redirect to the login page instead.
useAuth()
  .init()
  .then(() => createApp(App).use(router).mount('#app'))
  .catch((error) => {
    console.error('Login failed', error)
    document.getElementById('app')!.textContent = 'Login failed. Please reload the page.'
  })
