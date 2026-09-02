/**
 * 请求级 traceId 生成（design.md §9.7：前端生成 UUID 经 X-Trace-Id 透传，
 * 与后端 AdminTraceFilter / api_log.trace_id 对齐）。
 */
export function genTraceId(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) {
    return crypto.randomUUID()
  }
  // 兜底实现
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    const v = c === 'x' ? r : (r & 0x3) | 0x8
    return v.toString(16)
  })
}
