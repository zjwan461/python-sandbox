/** 角色管理接口（/admin-api/roles，权限 role:*） */
import { request } from '@/utils/request'
import type { AdminRole, PageResult } from '@/utils/types'

export function pageRoles(params: {
  roleName?: string
  roleKey?: string
  status?: number
  pageNum?: number
  pageSize?: number
}) {
  return request<PageResult<AdminRole>>({ url: '/roles/list', method: 'get', params })
}

export function roleOptions() {
  return request<AdminRole[]>({ url: '/roles/options', method: 'get' })
}

export function createRole(data: AdminRole) {
  return request<number>({ url: '/roles', method: 'post', data })
}

export function updateRole(id: number, data: AdminRole) {
  return request<void>({ url: `/roles/${id}`, method: 'put', data })
}

export function deleteRole(id: number) {
  return request<void>({ url: `/roles/${id}`, method: 'delete' })
}

export function changeRoleStatus(id: number, status: number) {
  return request<void>({ url: `/roles/${id}/status`, method: 'put', params: { status } })
}

export function roleMenuIds(id: number) {
  return request<number[]>({ url: `/roles/${id}/menus`, method: 'get' })
}

export function assignRoleMenus(id: number, menuIds: number[]) {
  return request<void>({ url: `/roles/${id}/menus`, method: 'put', data: menuIds })
}
