/** 认证接口（/admin-api/auth：captcha/login/logout/whoami/password） */
import { request } from '@/utils/request'
import type { CaptchaVO, LoginResult, WhoamiVO } from '@/utils/types'

export function getCaptcha() {
  return request<CaptchaVO>({ url: '/auth/captcha', method: 'get' })
}

export function login(data: {
  username: string
  password: string
  captchaId: string
  captchaAnswer: string
  rememberMe?: boolean
}) {
  return request<LoginResult>({ url: '/auth/login', method: 'post', data })
}

/**
 * 自动续登（T-0034）：凭 HttpOnly Cookie 中的 Remember-Me 长期 token 换取短期 token。
 * 无有效长期 token 时后端返回 data=null（不视为错误）。
 */
export function autoLogin() {
  return request<LoginResult | null>({ url: '/auth/auto-login', method: 'post' })
}

export function logout() {
  return request<void>({ url: '/auth/logout', method: 'post' })
}

export function whoami() {
  return request<WhoamiVO>({ url: '/auth/whoami', method: 'get' })
}

export function changePassword(data: { oldPassword: string; newPassword: string; confirmPassword: string }) {
  return request<void>({ url: '/auth/password', method: 'put', data })
}

export function tokenTtl() {
  return request<number>({ url: '/auth/token-ttl', method: 'get' })
}
