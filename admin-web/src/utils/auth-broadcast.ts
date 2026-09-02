/**
 * 认证失效广播（T-0015/T-0038）：解耦 request.ts 与 router/store 的循环依赖。
 * 由 router/index.ts 注册回调，完成"清理状态 + 跳转登录页"。
 */
let handler: ((msg?: string) => void) | null = null

export function registerAuthExpiredHandler(fn: (msg?: string) => void): void {
  handler = fn
}

export function handleAuthExpired(msg?: string): void {
  if (handler) {
    handler(msg)
  } else {
    // 兜底：直接跳转（登录页守卫会清理 Pinia 态）
    window.location.href = '/login'
  }
}
