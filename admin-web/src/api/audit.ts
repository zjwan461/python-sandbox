/** 管理端审计查询接口（/admin-api/audit/**，loginlog:view / oplog:view，只读） */
import { request } from '@/utils/request'
import type { AdminLoginLog, AdminOpLog, PageResult } from '@/utils/types'

export function pageLoginLogs(params: {
  username?: string
  result?: string
  beginTime?: string
  endTime?: string
  pageNum?: number
  pageSize?: number
}) {
  return request<PageResult<AdminLoginLog>>({ url: '/audit/logins', method: 'get', params })
}

export function pageOpLogs(params: {
  module?: string
  operationType?: string
  operatorName?: string
  targetId?: string
  beginTime?: string
  endTime?: string
  pageNum?: number
  pageSize?: number
}) {
  return request<PageResult<AdminOpLog>>({ url: '/audit/operations', method: 'get', params })
}

export function getOpLog(id: number) {
  return request<AdminOpLog>({ url: `/audit/operations/${id}`, method: 'get' })
}
