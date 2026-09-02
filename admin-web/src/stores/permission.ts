/**
 * 菜单/路由态（T-0038，design.md §9.6）：
 * 登录后拉取 GET /menus/routes，递归转换为动态路由 addRoute；
 * 刷新时经 routesLoaded 标记 + whoami 重取恢复，不产生重复路由或权限泄露。
 */
import { defineStore } from 'pinia'
import { ref, shallowRef } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import { myRoutes } from '@/api/menu'
import type { MenuRouteVO } from '@/utils/types'
import { buildRoutes } from '@/router/dynamic'

export const usePermissionStore = defineStore('permission', () => {
  /** 侧栏菜单树（后端下发原始树，isVisible=0 的节点渲染时过滤） */
  const menuTree = ref<MenuRouteVO[]>([])
  /** 已注册的动态路由（用于登出时逐一 removeRoute） */
  const dynamicRoutes = shallowRef<RouteRecordRaw[]>([])
  const routesLoaded = ref(false)

  async function generateRoutes(): Promise<RouteRecordRaw[]> {
    const tree = await myRoutes()
    menuTree.value = tree || []
    const routes = buildRoutes(tree || [])
    dynamicRoutes.value = routes
    routesLoaded.value = true
    return routes
  }

  function resetRoutes(): void {
    menuTree.value = []
    dynamicRoutes.value = []
    routesLoaded.value = false
  }

  return { menuTree, dynamicRoutes, routesLoaded, generateRoutes, resetRoutes }
})
