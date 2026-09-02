/** 限流规则接口（/admin-api/ratelimits，权限 ratelimit:*） */
import { request } from '@/utils/request'
import type { PageResult, RatelimitRule, RatelimitUpsertRequest } from '@/utils/types'

export interface RatelimitQuery {
  dimension?: string
  targetId?: number
  windowType?: string
  status?: number
  orderBy?: string
  asc?: boolean
  pageNum?: number
  pageSize?: number
}

export function pageRules(params: RatelimitQuery) {
  return request<PageResult<RatelimitRule>>({ url: '/ratelimits', method: 'get', params })
}

export function getRule(id: number) {
  return request<RatelimitRule>({ url: `/ratelimits/${id}`, method: 'get' })
}

export function createRule(data: RatelimitUpsertRequest) {
  return request<number>({ url: '/ratelimits', method: 'post', data })
}

export function updateRule(id: number, data: RatelimitUpsertRequest) {
  return request<void>({ url: `/ratelimits/${id}`, method: 'put', data })
}

export function changeRuleStatus(id: number, status: number) {
  return request<void>({ url: `/ratelimits/${id}/status`, method: 'put', params: { status } })
}

export function deleteRule(id: number) {
  return request<void>({ url: `/ratelimits/${id}`, method: 'delete' })
}

/** 管理员手动触发 python-sandbox 立即重拉规则 */
export function reloadRules() {
  return request<{ success: boolean }>({ url: '/ratelimits/reload', method: 'post' })
}
