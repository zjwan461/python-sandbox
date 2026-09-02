import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import * as ElIcons from '@element-plus/icons-vue'
import 'element-plus/dist/index.css'
import '@/styles/index.css'
import App from './App.vue'
import router, { setupDynamicRoutes } from './router'
import { createPinia } from 'pinia'
import { setupPermissionDirective } from './directives/permission'
import { getToken } from '@/utils/session'

async function bootstrap() {
  const app = createApp(App)
  app.use(createPinia())

  // 挂载前预加载动态路由：刷新深链接（如 /audit/operation）时先完成注册，
  // 避免初始导航匹配不到路由产生 "No match found" 告警与组件卸载异常
  if (getToken()) {
    try {
      await setupDynamicRoutes()
    } catch {
      /* 拉取失败静默：守卫会清态并回登录页 */
    }
  }

  app.use(router)
  app.use(ElementPlus, { locale: zhCn })

  // Element Plus 图标全局注册（菜单 icon 以后端 menu.icon 字符串动态取用）
  for (const [name, comp] of Object.entries(ElIcons)) {
    app.component(name, comp)
  }

  setupPermissionDirective(app)

  app.mount('#app')
}

bootstrap()
