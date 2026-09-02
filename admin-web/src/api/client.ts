/** 客户端管理接口（/admin-api/clients，权限 client:view/add/edit/delete/disable） */
import { request } from '@/utils/request'
import type { ClientApp, ClientUpsertRequest, PageResult } from '@/utils/types'

export interface ClientQuery {
  clientName?: string
  clientCode?: string
  ownerUserId?: number
  status?: number
  orderBy?: string
  asc?: boolean
  pageNum?: number
  pageSize?: number
}

export function pageClients(params: ClientQuery) {
  return request<PageResult<ClientApp>>({ url: '/clients', method: 'get', params })
}

export function getClient(id: number) {
  return request<ClientApp>({ url: `/clients/${id}`, method: 'get' })
}

export function createClient(data: ClientUpsertRequest) {
  return request<number>({ url: '/clients', method: 'post', data })
}

export function updateClient(id: number, data: ClientUpsertRequest) {
  return request<void>({ url: `/clients/${id}`, method: 'put', data })
}

export function changeClientStatus(id: number, status: number) {
  return request<void>({ url: `/clients/${id}/status`, method: 'put', params: { status } })
}

export function deleteClient(id: number) {
  return request<void>({ url: `/clients/${id}`, method: 'delete' })
}

/** 统计卡片（T-0035：ApiKey 数/活跃数/今日调用/累计调用） */
export function clientStats(id: number) {
  return request<import('@/utils/types').ClientStatsVO>({ url: `/clients/${id}/stats`, method: 'get' })
}

/** 归属转移（T-0035，仅管理员） */
export function transferClientOwner(id: number, ownerUserId: number) {
  return request<void>({ url: `/clients/${id}/owner`, method: 'put', data: { ownerUserId } })
}
