/**
 * 统一 Axios 封装（T-0011，design.md §9.7、§10）。
 *
 * - 基址 /admin-api（context-path 已在后端，故 baseURL 即 /admin-api）
 * - 请求头注入短期 token（Bearer，取自 Pinia/sessionStorage 镜像）+ X-Trace-Id
 * - withCredentials 携带 HttpOnly Cookie（Remember-Me 通道预留，T-0034）
 * - 响应按业务 code 语义分发：
 *   0 成功 | 20001 未登录跳登录 | 20004 被踢下线（清态跳登录并提示）
 *   20002/20003 权限/角色不足（提示，页面级可跳 403）| 429/30006 限流提示
 *   其余非 0：ElMessage 错误提示并 reject
 */
import axios, { AxiosError, type AxiosInstance, type AxiosRequestConfig, type InternalAxiosRequestConfig } from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { R } from './types'
import { genTraceId } from './trace'
import { clearToken, getToken } from './session'
import { handleAuthExpired } from './auth-broadcast'

export const CODE_SUCCESS = 0
export const CODE_NOT_LOGIN = 20001
export const CODE_NO_PERMISSION = 20002
export const CODE_NO_ROLE = 20003
export const CODE_KICKED_OUT = 20004
export const CODE_ACCOUNT_DISABLED = 11004
export const CODE_RATE_LIMIT = 30006

let notifiedKickedOut = false

const service: AxiosInstance = axios.create({
  baseURL: '/admin-api',
  timeout: 20000,
  withCredentials: true
})

service.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = getToken()
  if (token) {
    // Sa-Token 配置 token-prefix: Bearer
    config.headers.set('Authorization', `Bearer ${token}`)
  }
  config.headers.set('X-Trace-Id', genTraceId())
  return config
})

service.interceptors.response.use(
  (response) => {
    const body = response.data as R
    if (body == null || typeof body.code !== 'number') {
      // 非标准响应（理论上不发生）
      return response
    }
    if (body.code === CODE_SUCCESS) {
      return response
    }
    // ===== 业务错误语义分发 =====
    switch (body.code) {
      case CODE_KICKED_OUT:
        if (!notifiedKickedOut) {
          notifiedKickedOut = true
          clearToken()
          ElMessageBox.alert(body.message || '账号已在其他设备登录，当前会话已失效', '会话失效', {
            confirmButtonText: '重新登录',
            callback: () => {
              notifiedKickedOut = false
              handleAuthExpired(body.message)
            }
          })
        }
        break
      case CODE_NOT_LOGIN:
        clearToken()
        handleAuthExpired(body.message)
        break
      case CODE_ACCOUNT_DISABLED:
        clearToken()
        ElMessage.error(body.message || '账号已停用')
        handleAuthExpired(body.message)
        break
      case CODE_NO_PERMISSION:
      case CODE_NO_ROLE:
        ElMessage.error(body.message || '权限不足')
        break
      case 429:
      case CODE_RATE_LIMIT:
        ElMessage.warning(body.message || '请求过于频繁，请稍后再试')
        break
      default:
        ElMessage.error(body.message || '请求失败')
    }
    return Promise.reject(Object.assign(new Error(body.message || `code=${body.code}`), { bizCode: body.code, bizData: body }))
  },
  (error: AxiosError<R>) => {
    // HTTP 层错误（网关/网络），后端业务错误一般走 HTTP 200 + code
    const status = error.response?.status
    if (status === 401) {
      clearToken()
      handleAuthExpired('登录已过期')
    } else if (status === 403) {
      ElMessage.error('无访问权限')
    } else if (status === 429) {
      ElMessage.warning('请求过于频繁，请稍后再试')
    } else {
      ElMessage.error(error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

/** 通用请求：返回统一响应的 data 体 */
export async function request<T = any>(config: AxiosRequestConfig): Promise<T> {
  const res = await service.request<R<T>>(config)
  return res.data.data
}

export default service
