/**
 * 动态路由构建（T-0038，design.md §9.6）。
 *
 * 后端 GET /menus/routes 返回 MenuRouteVO 树（M=目录 C=菜单）：
 * - 顶级目录（M，如 /system /business /audit）挂到 Default Layout 下；
 * - 菜单（C）的 component 字段为相对 views 的路径（如 system/user/index），
 *   经 import.meta.glob 映射为懒加载组件；未匹配到组件的菜单跳过并告警（不产生坏路由）。
 */
import type { RouteRecordRaw } from 'vue-router'
import DefaultLayout from '@/layouts/DefaultLayout.vue'
import type { MenuRouteVO } from '@/utils/types'

// views 下所有页面组件（构建期收集；相对路径 glob，避免别名解析差异）
const viewModules = import.meta.glob('../views/**/*.vue')

function resolveComponent(component?: string): (() => Promise<any>) | null {
  if (!component) return null
  const suffix = `/views/${component.replace(/^\/+/, '')}.vue`
  const key = Object.keys(viewModules).find((k) => k.endsWith(suffix))
  if (!key) {
    console.warn(`[router] 菜单组件未找到: ${component}`)
    return null
  }
  return viewModules[key] as () => Promise<any>
}

function toRoute(node: MenuRouteVO): RouteRecordRaw | null {
  const comp = resolveComponent(node.component)
  if (!comp) return null
  return {
    path: (node.routePath || '').replace(/^\/+/, ''),
    name: node.routeName || `Menu${node.id}`,
    component: comp,
    meta: {
      title: node.menuName,
      icon: node.icon,
      perms: node.perms,
      external: node.isExternal === 1,
      keepAlive: node.isCache === 1
    }
  } as RouteRecordRaw
}

/** 将后端菜单树转换为挂在 Layout 下的扁平二级路由（目录/页面） */
export function buildRoutes(tree: MenuRouteVO[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []
  const walk = (nodes: MenuRouteVO[], parentPath: string) => {
    for (const n of nodes) {
      if (n.menuType === 'M') {
        // 目录：仅当含可见子菜单时递归；路径拼接
        const dirPath = joinPath(parentPath, n.routePath || '')
        if (n.children?.length) {
          walk(n.children, dirPath)
        }
      } else if (n.menuType === 'C') {
        const r = toRoute(n)
        if (r) {
          // 使用绝对路径注册，避免层级拼接歧义
          r.path = (n.routePath || joinPath(parentPath, r.path)).replace(/\/{2,}/g, '/')
          routes.push(r)
        }
      }
    }
  }
  walk(tree, '')
  return routes
}

function joinPath(parent: string, child: string): string {
  if (!child) return parent
  if (child.startsWith('/')) return child
  return `${parent.replace(/\/+$/, '')}/${child}`
}

/** Layout 容器路由（动态路由统一挂在此 name 下） */
export const LAYOUT_ROUTE_NAME = 'LayoutRoot'

export function layoutRoute(children: RouteRecordRaw[]): RouteRecordRaw {
  return {
    path: '/',
    name: LAYOUT_ROUTE_NAME,
    component: DefaultLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '工作台', icon: 'HomeFilled' }
      },
      ...children
    ]
  }
}
