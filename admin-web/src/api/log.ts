/** 调用记录查询接口（/admin-api/logs/**，权限 apilog:view 只读；apilog:export 导出） */
import service, { request } from '@/utils/request'
import { ElMessage } from 'element-plus'
import type { ApiLogVO, DetectLogVO, PageResult, SandboxLogVO, TraceDetailVO } from '@/utils/types'

export interface ApiLogQuery {
  beginTime?: string
  endTime?: string
  apiKeyId?: number
  clientId?: number
  ownerUserId?: number
  httpMethod?: string
  apiPath?: string
  responseCode?: number
  traceId?: string
  clientIp?: string
  sessionId?: string
  rateLimitHit?: boolean
  orderBy?: string
  asc?: boolean
  pageNum?: number
  pageSize?: number
}

export function pageApiLogs(params: ApiLogQuery) {
  return request<PageResult<ApiLogVO>>({ url: '/logs/api', method: 'get', params })
}

export function getApiLog(id: number) {
  return request<ApiLogVO>({ url: `/logs/api/${id}`, method: 'get' })
}

export interface SandboxLogQuery {
  beginTime?: string
  endTime?: string
  operationType?: string
  result?: string
  traceId?: string
  sessionId?: string
  clientId?: number
  apiKeyId?: number
  ownerUserId?: number
  orderBy?: string
  asc?: boolean
  pageNum?: number
  pageSize?: number
}

export function pageSandboxLogs(params: SandboxLogQuery) {
  return request<PageResult<SandboxLogVO>>({ url: '/logs/sandbox', method: 'get', params })
}

export function getSandboxLog(id: number) {
  return request<SandboxLogVO>({ url: `/logs/sandbox/${id}`, method: 'get' })
}

// ===================== CodeGuard 模型检测记录 =====================

export interface DetectLogQuery {
  beginTime?: string
  endTime?: string
  label?: string
  decision?: string
  detectStatus?: string
  traceId?: string
  sessionId?: string
  clientId?: number
  apiKeyId?: number
  ownerUserId?: number
  orderBy?: string
  asc?: boolean
  pageNum?: number
  pageSize?: number
}

export function pageDetectLogs(params: DetectLogQuery) {
  return request<PageResult<DetectLogVO>>({ url: '/logs/detect', method: 'get', params })
}

export function getDetectLog(id: number) {
  return request<DetectLogVO>({ url: `/logs/detect/${id}`, method: 'get' })
}

/** traceId 链路聚合：一次请求的 API 日志 + 多次沙箱操作同屏（FR-LOG-03） */
export function getTraceDetail(traceId: string) {
  return request<TraceDetailVO>({ url: `/logs/trace/${traceId}`, method: 'get' })
}

// ===================== T-0045 导出 =====================

/** 从 content-disposition 解析文件名并触发浏览器下载 */
function triggerDownload(blob: Blob, disposition: unknown, fallback: string): void {
  let fileName = fallback
  if (typeof disposition === 'string') {
    const utf8Match = /filename\*=UTF-8''([^;]+)/i.exec(disposition)
    const plainMatch = /filename="?([^";]+)"?/i.exec(disposition)
    if (utf8Match) fileName = decodeURIComponent(utf8Match[1])
    else if (plainMatch) fileName = plainMatch[1]
  }
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}

/**
 * API 日志导出（FR-LOG-06）：与当前筛选/排序/数据权限一致；format=csv|excel。
 * 权限 apilog:export；后端保留截断标记列，不含敏感凭证。
 */
export async function exportApiLogs(params: ApiLogQuery, format: 'csv' | 'excel'): Promise<void> {
  const res = await service.get('/logs/api/export', {
    params: { ...params, format },
    responseType: 'blob'
  })
  triggerDownload(
    res.data as Blob,
    res.headers['content-disposition'],
    `api_log.${format === 'excel' ? 'xls' : 'csv'}`
  )
  ElMessage.success('导出已开始')
}

// ===================== 大模型复检 =====================

export interface LlmReviewQuery {
  beginTime?: string
  endTime?: string
  detectLogId?: number
  taskStatus?: string
  llmLabel?: string
  humanReviewStatus?: string
  humanLabel?: string
  orderBy?: string
  asc?: boolean
  pageNum?: number
  pageSize?: number
}

export interface LlmReviewVO {
  id: number
  detectLogId: number
  taskStatus: string
  retryCount: number
  maxRetry: number
  codeSnippet?: string
  smallModelLabel?: string
  smallModelRawOutput?: string
  llmProvider?: string
  llmModel?: string
  llmLabel?: string
  llmExplanation?: string
  llmLatencyMs?: number
  llmErrorMessage?: string
  humanReviewStatus?: string
  humanLabel?: string
  humanRemark?: string
  reviewerId?: number
  reviewerName?: string
  reviewTime?: string
  startTime?: string
  endTime?: string
  createdAt?: string
  updatedAt?: string
}

export function pageLlmReviews(params: LlmReviewQuery) {
  return request<PageResult<LlmReviewVO>>({ url: '/logs/llm-review', method: 'get', params })
}

export function getLlmReview(id: number) {
  return request<LlmReviewVO>({ url: `/logs/llm-review/${id}`, method: 'get' })
}

export function createLlmReview(detectLogId: number) {
  return request<number>({ url: '/logs/llm-review', method: 'post', params: { detectLogId } })
}

export function batchCreateLlmReview(detectLogIds: number[]) {
  return request<{ success: number; skipped: number; failed: number; total: number; errors: string[] }>({
    url: '/logs/llm-review/batch',
    method: 'post',
    data: detectLogIds
  })
}

export function humanReviewLlm(id: number, data: { humanReviewStatus: string; humanLabel?: string; humanRemark?: string }) {
  return request<void>({ url: `/logs/llm-review/${id}/review`, method: 'put', data })
}

export function cancelLlmReview(id: number) {
  return request<void>({ url: `/logs/llm-review/${id}/cancel`, method: 'put' })
}

export function executeLlmReview(id: number) {
  return request<void>({ url: `/logs/llm-review/${id}/execute`, method: 'post' })
}

export async function exportLlmReviewsJsonl(params: LlmReviewQuery): Promise<void> {
  const res = await service.get('/logs/llm-review/export/jsonl', {
    params,
    responseType: 'blob'
  })
  triggerDownload(
    res.data as Blob,
    res.headers['content-disposition'],
    'llm_review_export.jsonl'
  )
  ElMessage.success('导出已开始')
}

/** 沙箱操作日志导出（FR-LOG-06，口径同上） */
export async function exportSandboxLogs(params: SandboxLogQuery, format: 'csv' | 'excel'): Promise<void> {
  const res = await service.get('/logs/sandbox/export', {
    params: { ...params, format },
    responseType: 'blob'
  })
  triggerDownload(
    res.data as Blob,
    res.headers['content-disposition'],
    `sandbox_op_log.${format === 'excel' ? 'xls' : 'csv'}`
  )
  ElMessage.success('导出已开始')
}

/** CodeGuard 模型检测记录导出（口径同上；导出含代码原文，供再训练样本提取） */
export async function exportDetectLogs(params: DetectLogQuery, format: 'csv' | 'excel'): Promise<void> {
  const res = await service.get('/logs/detect/export', {
    params: { ...params, format },
    responseType: 'blob'
  })
  triggerDownload(
    res.data as Blob,
    res.headers['content-disposition'],
    `codeguard_detect_log.${format === 'excel' ? 'xls' : 'csv'}`
  )
  ElMessage.success('导出已开始')
}
