import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from "element-plus"
import 'element-plus/dist/index.css'
import './assets/tailwind.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'
import axios from 'axios'

const app = createApp(App)
app.config.globalProperties.$http = axios
const pinia=createPinia()
app.use(pinia)
app.use(ElementPlus)
app.use(router)
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
