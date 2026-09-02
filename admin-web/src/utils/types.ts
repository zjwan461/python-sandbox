/**
 * 与 admin-server 接口契约对齐的公共类型（批次3/4 已实现契约）。
 */

/** 统一响应包装：{code,message,data,traceId,timestamp}，code=0 成功 */
export interface R<T = any> {
  code: number
  message: string
  data: T
  traceId?: string
  timestamp: number
}

/** 分页结构 data={list,total,pageNum,pageSize} */
export interface PageResult<T> {
  list: T[]
  total: number
  pageNum: number
  pageSize: number
}

// ===== 认证 =====
export interface CaptchaVO {
  captchaId: string
  img: string
  expireSeconds: number
}

export interface LoginResult {
  token: string
  tokenTimeout: number
  firstLogin: boolean
  userId: number
  username: string
}

// ===== 系统设置（T-0041）=====
export interface SysConfigItem {
  id: number
  configKey: string
  configValue: string
  valueType: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON'
  configName: string
  description?: string
  isBuiltIn?: number
  updateTime?: string
}

// ===== 通知公告（T-0042）=====
export interface SysNoticeVO {
  id: number
  title: string
  content: string
  effectiveTime?: string
  expireTime?: string
  top: boolean
  status?: number
  publisherName?: string
  publishTime?: string
  createTime?: string
  read: boolean
  inWindow?: boolean
}

export interface NoticeUpsertRequest {
  title: string
  content: string
  effectiveTime?: string | null
  expireTime?: string | null
  isTop?: number
}

// ===== 客户端统计（T-0035）=====
export interface ClientStatsVO {
  apiKeyCount: number
  activeApiKeyCount: number
  todayCalls: number
  totalCalls: number
}

// ===== 用户导入结果（T-0043）=====
export interface UserImportResult {
  successUsernames: string[]
  failures: { row: number; reason: string }[]
  total: number
}

export interface WhoamiVO {
  userId: number
  username: string
  nickname: string
  roles: string[]
  permissions: string[]
  firstLogin: boolean
  dataScope: 'ALL' | 'SELF'
}

// ===== 动态路由（GET /menus/routes）=====
export interface MenuRouteVO {
  id: number
  menuType: 'M' | 'C'
  menuName: string
  icon?: string
  sortOrder?: number
  routePath?: string
  routeName?: string
  component?: string
  isExternal?: number
  isCache?: number
  isVisible?: number
  perms?: string
  children: MenuRouteVO[]
}

// ===== 菜单管理（/menus/tree 返回 MenuTreeVO extends AdminMenu）=====
export interface AdminMenu {
  id?: number
  parentId: number
  menuType: 'M' | 'C' | 'F'
  menuName: string
  icon?: string
  sortOrder?: number
  routePath?: string
  routeName?: string
  component?: string
  isExternal?: number
  isCache?: number
  isVisible?: number
  perms?: string
  status?: number
  createTime?: string
  updateTime?: string
  children?: AdminMenu[]
}

// ===== 用户 =====
export interface RoleBrief {
  id: number
  roleName: string
  roleKey: string
}

export interface UserVO {
  id: number
  username: string
  nickname?: string
  email?: string
  phone?: string
  avatar?: string
  status: number
  deptName?: string
  locked?: boolean
  lockExpireTime?: string
  firstLogin?: number
  lastLoginTime?: string
  remark?: string
  createTime?: string
  roles?: RoleBrief[]
}

export interface UserUpsertRequest {
  username: string
  nickname?: string
  email?: string
  phone?: string
  password?: string
  status?: number
  deptName?: string
  remark?: string
  roleIds?: number[]
}

// ===== 角色 =====
export interface AdminRole {
  id?: number
  roleName: string
  roleKey: string
  sortOrder?: number
  status?: number
  builtIn?: number
  remark?: string
  createTime?: string
}

// ===== 客户端 =====
export interface ClientApp {
  id: number
  clientCode: string
  clientName: string
  description?: string
  ownerUserId?: number
  status: number
  remark?: string
  createTime?: string
}

export interface ClientUpsertRequest {
  clientCode: string
  clientName: string
  description?: string
  ownerUserId?: number | null
  status?: number
  remark?: string
}

// ===== ApiKey =====
export interface ApiKeyVO {
  id: number
  name: string
  clientId: number
  clientCode?: string
  clientName?: string
  boundUserId?: number
  boundUserName?: string
  keyPrefix: string
  keySuffixMask: string
  effectiveTime?: string
  expireTime?: string
  status: number // 1=启用 2=停用 3=已过期 4=已撤销
  statusLabel?: string
  rateLimitExempt?: number
  plaintextOneShot?: number
  remark?: string
  createTime?: string
}

export interface ApiKeyCreateVO {
  apiKey: ApiKeyVO
  plaintext: string
  notice?: string
}

export interface ApiKeyUpsertRequest {
  name: string
  clientId: number
  boundUserId?: number | null
  effectiveTime?: string | null
  expireTime?: string | null
  rateLimitExempt?: number
  remark?: string
}

// ===== 限流规则 =====
export interface RatelimitRule {
  id: number
  dimension: 'API_KEY' | 'CLIENT' | 'GLOBAL'
  targetId: number
  windowType: 'MINUTE' | 'HOUR' | 'DAY'
  threshold: number
  priority?: number
  status: number
  effectiveTime?: string
  expireTime?: string
  remark?: string
  createTime?: string
}

export interface RatelimitUpsertRequest {
  dimension: string
  targetId: number
  windowType: string
  threshold: number
  priority?: number
  status?: number
  effectiveTime?: string | null
  expireTime?: string | null
  remark?: string
}

// ===== 会话 =====
export interface SandboxSessionVO {
  sessionId: string
  containerId?: string
  containerName?: string
  createTime?: string
  lastActiveTime?: string
  isDefault?: boolean
  ownerClientId?: number
  ownerApiKeyId?: number
  ownerUserId?: number
  ownerClientCode?: string
  ownerApiKeyLabel?: string
  ownerUserName?: string
}

export interface SessionDestroyResultVO {
  success: boolean
  message?: string
  remainingSessions?: number
  sessionId?: string
}

// ===== 日志 =====
export interface ApiLogVO {
  id: number
  traceId?: string
  sessionId?: string
  apiPath?: string
  httpMethod?: string
  requestParams?: string
  requestParamsTruncated?: boolean
  responseCode?: number
  executionTime?: number
  clientIp?: string
  createdAt?: string
  clientId?: number
  clientCode?: string
  apiKeyId?: number
  apiKeyLabel?: string
  ownerUserId?: number
  ownerUserName?: string
  rateLimitHit?: number
  rateLimitRuleId?: number
}

export interface SandboxLogVO {
  id: number
  traceId?: string
  sessionId?: string
  operationType?: string
  operationContent?: string
  operationContentTruncated?: boolean
  result?: string
  exitCode?: number
  stdout?: string
  stdoutTruncated?: boolean
  stderr?: string
  stderrTruncated?: boolean
  executionTime?: number
  errorMessage?: string
  createdAt?: string
  clientId?: number
  clientCode?: string
  apiKeyId?: number
  apiKeyLabel?: string
  ownerUserId?: number
  ownerUserName?: string
}

export interface TraceDetailVO {
  traceId: string
  apiLogs: ApiLogVO[]
  operationLogs: SandboxLogVO[]
}

// ===== 审计 =====
export interface AdminLoginLog {
  id: number
  username: string
  userId?: number
  loginType?: string
  result: 'SUCCESS' | 'FAIL' | 'LOCKED'
  failReason?: string
  ip?: string
  userAgent?: string
  loginTime?: string
}

export interface AdminOpLog {
  id: number
  operatorId?: number
  operatorName?: string
  module?: string
  operationType?: string
  targetId?: string
  targetName?: string
  changeSummary?: string
  result?: string
  failReason?: string
  ip?: string
  userAgent?: string
  traceId?: string
  opTime?: string
}
