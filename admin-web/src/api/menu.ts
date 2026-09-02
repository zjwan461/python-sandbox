/** 菜单管理接口（/admin-api/menus，权限 menu:*；/routes 仅需登录） */
import { request } from '@/utils/request'
import type { AdminMenu, MenuRouteVO } from '@/utils/types'

export function menuTree() {
  return request<AdminMenu[]>({ url: '/menus/tree', method: 'get' })
}

export function myRoutes() {
  return request<MenuRouteVO[]>({ url: '/menus/routes', method: 'get' })
}

export function createMenu(data: AdminMenu) {
  return request<number>({ url: '/menus', method: 'post', data })
}

export function updateMenu(id: number, data: AdminMenu) {
  return request<void>({ url: `/menus/${id}`, method: 'put', data })
}
export function deleteMenu(id: number) {
  return request<void>({ url: `/menus/${id}`, method: 'delete' })
}

/** 批量排序（T-0039：同父级拖拽后提交有序ID清单） */
export function batchSortMenus(orderedIds: number[]) {
  return request<void>({ url: '/menus/batch-sort', method: 'put', data: orderedIds })
}

/** 可见性切换（T-0039：0=隐藏 1=显示，立即反映到当前用户可见路由） */
export function changeMenuVisible(id: number, isVisible: number) {
  return request<void>({ url: `/menus/${id}/visible`, method: 'put', params: { isVisible } })
}
