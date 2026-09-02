/**
 * 路由（T-0038，design.md §9.6）：
 * 静态路由（登录/403/404 + 个人中心）+ 登录后按 /menus/routes 递归注册动态路由。
 * 守卫：未登录 → /login；已登录访问 /login → /；动态路由未加载则先加载再重放导航；
 * 刷新恢复：token 存在于 sessionStorage 时重新拉取 whoami+routes，不产生重复注册。
 */
import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { getToken } from '@/utils/session'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { layoutRoute, LAYOUT_ROUTE_NAME } from './dynamic'
import { registerAuthExpiredHandler } from '@/utils/auth-broadcast'

const staticRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/login.vue'),
    meta: { public: true, title: '登录' }
  },
  {
    path: '/403',
    name: 'Forbidden',
    component: () => import('@/views/error/403.vue'),
    meta: { public: true, title: '无权限' }
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: { public: true, title: '页面不存在' }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes: staticRoutes
})

let initPromise: Promise<void> | null = null

/** 注册动态路由 + 404 兜底（main.ts 挂载前预加载与守卫共用，幂等） */
export async function setupDynamicRoutes(): Promise<void> {
  const userStore = useUserStore()
  const permStore = usePermissionStore()
  if (!userStore.userInfo) {
    await userStore.fetchWhoami()
  }
  const routes = await permStore.generateRoutes()
  // 重复注册保护：先移除旧的 LayoutRoot，避免并发/重放产生重名路由
  if (router.hasRoute(LAYOUT_ROUTE_NAME)) {
    router.removeRoute(LAYOUT_ROUTE_NAME)
  }
  router.addRoute(layoutRoute(routes))
  // 404 兜底在动态路由注册后追加，避免刷新误跳
  if (!router.hasRoute('CatchAll')) {
    router.addRoute({
      path: '/:pathMatch(.*)*',
      name: 'CatchAll',
      redirect: '/404',
      meta: { public: true }
    })
  }
}

/** 并发安全的初始化入口：多导航共享同一 Promise，完成后统一重放 */
function ensureDynamicRoutes(): Promise<void> {
  if (!initPromise) {
    initPromise = setupDynamicRoutes().catch((err) => {
      initPromise = null
      throw err
    })
  }
  return initPromise
}

/** 登出/认证失效统一清理：重置初始化缓存并移除已注册动态路由，避免重新登录时路由不重建 */
export function resetDynamicRoutes(): void {
  initPromise = null
  if (router.hasRoute(LAYOUT_ROUTE_NAME)) {
    router.removeRoute(LAYOUT_ROUTE_NAME)
  }
}

router.beforeEach(async (to) => {
  const token = getToken()
  const userStore = useUserStore()
  const permStore = usePermissionStore()

  // 白名单直达
  if (to.meta.public) {
    if (to.name === 'Login' && token) return { path: '/' }
    return true
  }
  if (!token) {
    return { path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : undefined }
  }

  // 首次登录强制改密：除改密/登录外全部跳转改密页（T-0017）
  if (userStore.userInfo && userStore.firstLogin && to.name !== 'ChangePassword') {
    return { path: '/change-password', query: { forced: '1' } }
  }

  if (!permStore.routesLoaded) {
    try {
      await ensureDynamicRoutes()
    } catch {
      userStore.reset()
      permStore.resetRoutes()
      return { path: '/login' }
    }
    if (userStore.firstLogin && to.name !== 'ChangePassword') {
      return { path: '/change-password', query: { forced: '1' }, replace: true }
    }
    // 动态路由注册完成后重放当前导航，使其命中新注册的子路由
    return { ...to, replace: true }
  }

  // 路由级权限：meta.perms 未命中 → 403
  const perms = to.meta?.perms as string | undefined
  if (perms && !userStore.permissions.has(perms) && !userStore.roles.includes('superadmin')) {
    return { path: '/403' }
  }
  return true
})

// 认证失效统一处理：清态 + 跳转登录（20001/20004/11004 与 HTTP 401 均汇入此处）
registerAuthExpiredHandler(() => {
  const userStore = useUserStore()
  const permStore = usePermissionStore()
  userStore.reset()
  permStore.resetRoutes()
  // 重置初始化缓存并移除已注册动态路由，避免残留/重新登录时不重建
  resetDynamicRoutes()
  const current = router.currentRoute.value
  router.replace({
    path: '/login',
    query: current.fullPath !== '/' ? { redirect: current.fullPath } : undefined
  })
})

export default router
