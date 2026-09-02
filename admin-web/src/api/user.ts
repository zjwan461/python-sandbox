/** 用户管理接口（/admin-api/users，权限 user:*） */
import service, { request } from '@/utils/request'
import { ElMessage } from 'element-plus'
import type { PageResult, UserImportResult, UserUpsertRequest, UserVO } from '@/utils/types'

export interface UserQuery {
  username?: string
  nickname?: string
  status?: number
  deptName?: string
  pageNum?: number
  pageSize?: number
}

export function pageUsers(params: UserQuery) {
  return request<PageResult<UserVO>>({ url: '/users', method: 'get', params })
}

export function getUser(id: number) {
  return request<UserVO>({ url: `/users/${id}`, method: 'get' })
}

export function createUser(data: UserUpsertRequest) {
  return request<number>({ url: '/users', method: 'post', data })
}

export function updateUser(id: number, data: UserUpsertRequest) {
  return request<void>({ url: `/users/${id}`, method: 'put', data })
}

export function changeUserStatus(id: number, status: number) {
  return request<void>({ url: `/users/${id}/status`, method: 'put', params: { status } })
}

export function resetUserPassword(id: number, newPassword: string) {
  return request<void>({ url: `/users/${id}/reset-password`, method: 'put', data: { newPassword } })
}

export function unlockUser(id: number) {
  return request<void>({ url: `/users/${id}/unlock`, method: 'put' })
}

export function assignUserRoles(id: number, roleIds: number[]) {
  return request<void>({ url: `/users/${id}/roles`, method: 'put', data: roleIds })
}

/** 单删（T-0018 批次6：软删除+归属转移；已登录/持有有效 ApiKey 后端 12006 阻止） */
export function deleteUser(id: number) {
  return request<void>({ url: `/users/${id}`, method: 'delete' })
}

/** 批量删除（任一目标不满足规则整批拒绝并透出原因） */
export function deleteUsers(ids: number[]) {
  return request<void>({ url: '/users/batch', method: 'delete', data: ids })
}

/** CSV 导出（T-0043：范围=当前筛选+数据权限；不含密码/ApiKey/凭证；user:export） */
export async function exportUsers(params: UserQuery) {
  const res = await service.get('/users/export', { params, responseType: 'blob' })
  let fileName = 'users.csv'
  const disposition = res.headers['content-disposition']
  const utf8Match = disposition ? /filename\*=UTF-8''([^;]+)/i.exec(disposition) : null
  if (utf8Match) fileName = decodeURIComponent(utf8Match[1])
  const url = window.URL.createObjectURL(res.data as Blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
  ElMessage.success('导出已开始')
}

/** CSV 批量导入（T-0043：user:import；逐行反馈成功/失败） */
export function importUsers(file: File, initialPassword: string) {
  const form = new FormData()
  form.append('file', file)
  form.append('initialPassword', initialPassword)
  return request<UserImportResult>({
    url: '/users/import',
    method: 'post',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
