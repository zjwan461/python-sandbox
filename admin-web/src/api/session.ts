/** 运行中会话接口（/admin-api/sessions，session:view / session:force） */
import { request } from '@/utils/request'
import type {
  ApiLogVO,
  PageResult,
  SandboxLogVO,
  SandboxSessionVO,
  SessionDestroyResultVO
} from '@/utils/types'

export interface SessionQuery {
  clientId?: number
  apiKeyId?: number
  ownerUserId?: number
  sessionId?: string
  createdBegin?: string
  createdEnd?: string
  isDefault?: boolean
  inactiveMinutes?: number
  pageNum?: number
  pageSize?: number
}

/** 会话关联日志（T-0037，FR-SESSION-03） */
export interface SessionRelatedLogsVO {
  session: SandboxSessionVO
  apiLogs: ApiLogVO[]
  operationLogs: SandboxLogVO[]
}

export function pageSessions(params: SessionQuery) {
  return request<PageResult<SandboxSessionVO>>({ url: '/sessions', method: 'get', params })
}

export function getSession(sessionId: string) {
  return request<SandboxSessionVO>({ url: `/sessions/${sessionId}`, method: 'get' })
}
/**
 * 强制销毁。回执 {success,message,remainingSessions}：
 * 失败时不虚构成功（默认决策 #7），调用方失败后不得从前端移除记录。
 */
export function destroySession(sessionId: string) {
  return request<SessionDestroyResultVO>({ url: `/sessions/${sessionId}`, method: 'delete' })
}

/** 会话关联日志（T-0037：最近 API 日志与沙箱操作日志，遵守数据权限） */
export function sessionRelatedLogs(sessionId: string, limit = 20) {
  return request<SessionRelatedLogsVO>({
    url: `/sessions/${sessionId}/logs`,
    method: 'get',
    params: { limit }
  })
}

/** 批量清理预览（T-0044：按不活跃阈值统计目标数量，默认会话不计入） */
export function batchDestroyPreview(inactiveMinutes: number) {
  return request<number>({ url: '/sessions/batch/preview', method: 'get', params: { inactiveMinutes } })
}

/** 批量强销（T-0044：仅管理员；逐项回执；自动阈值口径不隐式纳入默认会话） */
export function batchDestroySessions(payload: { inactiveMinutes?: number; sessionIds?: string[] }) {
  return request<SessionDestroyResultVO[]>({ url: '/sessions/batch-destroy', method: 'post', data: payload })
}
