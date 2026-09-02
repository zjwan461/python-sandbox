/**
 * 用户态（design.md §9.3）：token / userInfo / roles / permissions。
 * 短期 token 权威保存在本 store，并镜像至 sessionStorage 供刷新恢复与 Axios 注入；
 * 不写入 localStorage 等跨会话脚本可读存储（T-0015 验收）。
 */
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { autoLogin, login as loginApi, logout as logoutApi, whoami } from '@/api/auth'
import type { LoginResult, WhoamiVO } from '@/utils/types'
import { clearToken, getToken, setToken } from '@/utils/session'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(getToken())
  const userInfo = ref<WhoamiVO | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<Set<string>>(new Set())
  const firstLogin = ref(false)

  async function login(payload: {
    username: string
    password: string
    captchaId: string
    captchaAnswer: string
    rememberMe?: boolean
  }): Promise<LoginResult> {
    const res = await loginApi(payload)
    token.value = res.token
    setToken(res.token)
    firstLogin.value = res.firstLogin
    return res
  }

  /**
   * 自动续登（T-0034）：凭 HttpOnly Cookie 中的 Remember-Me 长期 token 换短期 token。
   * 长期 token 全程不进 Pinia/任何脚本可读存储（验收口径）；失败静默返回 false。
   */
  async function tryAutoLogin(): Promise<boolean> {
    try {
      const res = await autoLogin()
      if (res && res.token) {
        token.value = res.token
        setToken(res.token)
        firstLogin.value = res.firstLogin
        return true
      }
      return false
    } catch {
      return false
    }
  }

  async function fetchWhoami(): Promise<WhoamiVO> {
    const info = await whoami()
    userInfo.value = info
    roles.value = info.roles || []
    permissions.value = new Set(info.permissions || [])
    firstLogin.value = info.firstLogin
    return info
  }

  async function logout(): Promise<void> {
    try {
      await logoutApi()
    } finally {
      reset()
    }
  }

  /** 数据范围是否 ALL（管理员/审计员/超管） */
  const dataIsAll = computed(() => userInfo.value?.dataScope === 'ALL' || roles.value.includes('superadmin') || roles.value.includes('admin') || roles.value.includes('auditor'))

  /** 主动退出 / 被踢下线 / 未登录时的统一清理 */
  function reset(): void {
    token.value = ''
    userInfo.value = null
    roles.value = []
    permissions.value = new Set()
    firstLogin.value = false
    clearToken()
  }

  return { token, userInfo, roles, permissions, firstLogin, dataIsAll, login, tryAutoLogin, fetchWhoami, logout, reset }
})
