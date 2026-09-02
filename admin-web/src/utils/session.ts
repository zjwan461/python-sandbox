/**
 * 短期 token 存取（T-0015）。
 *
 * 口径：短期 token 的权威存储在 Pinia（useUserStore）；为满足页面刷新后
 * 会话连续（T-0038 动态路由刷新恢复），在 sessionStorage 做同页会话级镜像，
 * 标签页关闭即清除；不使用 localStorage 等跨会话持久化存储，
 * 长期免登 token 属 Remember-Me（T-0034）HttpOnly Cookie 通道，不在此处。
 */
const TOKEN_KEY = 'admin_session_token'

export function getToken(): string {
  return sessionStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token: string): void {
  sessionStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  sessionStorage.removeItem(TOKEN_KEY)
}
