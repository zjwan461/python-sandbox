/** ApiKey 管理接口（/admin-api/apikeys，权限 apikey:view/add/edit/disable/revoke/reset） */
import { request } from '@/utils/request'
import type { ApiKeyCreateVO, ApiKeyUpsertRequest, ApiKeyVO, PageResult } from '@/utils/types'

export interface ApiKeyQuery {
  name?: string
  clientId?: number
  boundUserId?: number
  status?: number
  expireBegin?: string
  expireEnd?: string
  orderBy?: string
  asc?: boolean
  pageNum?: number
  pageSize?: number
}

export function pageApiKeys(params: ApiKeyQuery) {
  return request<PageResult<ApiKeyVO>>({ url: '/apikeys', method: 'get', params })
}

export function getApiKey(id: number) {
  return request<ApiKeyVO>({ url: `/apikeys/${id}`, method: 'get' })
}

/** 创建：响应一次性携带明文（仅此一次，关闭后不可再取） */
export function createApiKey(data: ApiKeyUpsertRequest) {
  return request<ApiKeyCreateVO>({ url: '/apikeys', method: 'post', data })
}

export function updateApiKey(id: number, data: ApiKeyUpsertRequest) {
  return request<void>({ url: `/apikeys/${id}`, method: 'put', data })
}

export function changeApiKeyStatus(id: number, status: number) {
  return request<void>({ url: `/apikeys/${id}/status`, method: 'put', params: { status } })
}

/** 撤销：不可逆 */
export function revokeApiKey(id: number) {
  return request<void>({ url: `/apikeys/${id}/revoke`, method: 'put' })
}

/** 重新生成：旧钥即刻撤销，新明文一次性返回 */
export function regenerateApiKey(id: number) {
  return request<ApiKeyCreateVO>({ url: `/apikeys/${id}/regenerate`, method: 'post' })
}
