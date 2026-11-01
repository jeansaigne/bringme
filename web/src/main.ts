import { createApp } from 'vue'
import ui from '@nuxt/ui/vue-plugin'
import App from './App.vue'
import { router } from './router'
import '@fontsource-variable/inter'
import '@fontsource-variable/bricolage-grotesque'
import '@fontsource-variable/jetbrains-mono'
import './style.css'

createApp(App).use(router).use(ui).mount('#app')
