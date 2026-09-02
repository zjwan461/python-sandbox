import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElIcons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import '@/styles/index.css'
import App from './App.vue'
import router from './router'
import { createPinia } from 'pinia'
import { setupPermissionDirective } from './directives/permission'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus, { locale: zhCn })

// Element Plus 图标全局注册（菜单 icon 以后端 menu.icon 字符串动态取用）
for (const [name, comp] of Object.entries(ElIcons)) {
  app.component(name, comp)
}

setupPermissionDirective(app)

app.mount('#app')
