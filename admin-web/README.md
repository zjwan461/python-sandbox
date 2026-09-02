# admin-web — Python Sandbox 管理端前端

独立一级工程（T-0011 边界约定）：不引用 `python-sandbox/`、`admin-server/` 的任何源码；
所有后端交互仅经由 `/admin-api/**` HTTP 契约。

## 技术栈

- Vite 5 + Vue 3.5（`<script setup>` Composition API）+ TypeScript 5
- Element Plus 2（全量引入 + zh-cn locale + Icons 全局注册，菜单图标按后端 `menu.icon` 字符串动态取用）
- Pinia 2（`stores/user`（token/roles/permissions/dataScope/firstLogin）、`stores/permission`（菜单树/动态路由））
- Vue Router 4（静态路由 + 登录后台菜动态注册）
- Axios（统一封装 `src/utils/request.ts`）

## 与后端契约的对齐要点

| 契约 | 前端落点 |
|------|----------|
| 基址 `/admin-api/**` | `utils/request.ts` baseURL；dev 由 `vite.config.ts` 代理至 `http://localhost:9090` |
| 统一响应 `{code,message,data,traceId,timestamp}` | `R<T>` 类型 + 响应拦截器按 code 分发 |
| 分页 `{list,total,pageNum,pageSize}` | `PageResult<T>` 类型 |
| 短期 token | Pinia 权威 + sessionStorage 同页镜像（刷新恢复），`Authorization: Bearer` 头；不落 localStorage |
| Remember-Me（T-0034 预留） | `withCredentials: true` 携带 HttpOnly Cookie；前端不接触长期 token |
| `X-Trace-Id` | 请求级 UUID（`utils/trace.ts`） |
| 20001 未登录 | 清态跳 `/login` |
| 20004 被踢下线 | ElMessageBox 强调 → 清态（含动态路由移除）跳 `/login` |
| 11004 账号停用 | 清态跳登录并提示 |
| 20002/20003 | ElMessage 提示；路由 meta.perms 未命中 → `/403` |
| 429 / 30006 | 限流 warning 提示 |
| 30008 客户端删除阻断 / 30009 ApiKey 状态冲突 / 30010 规则重复 | 后端 message 经拦截器透出，页面确认框预说明业务后果 |
| 会话强销回执 | `success=false` 不移除前端行且展示失败原因（默认决策 #7） |
| ApiKey 明文一次性 | 仅创建/重新生成响应弹窗展示一次，关闭即丢弃（不入库不入 store） |
| 截断标记 | `*Truncated` 布尔渲染"已截断"标签（FR-LOG-04） |
| 首登强制改密 | `firstLogin` → 路由守卫锁定 `/change-password?forced=1` |

## 页面清单（与种子菜单 component 一一对齐）

- `views/auth/login.vue`（验证码登录）/ `change-password.vue`（T-0017）/ `profile.vue`（个人中心）
- `views/dashboard/index.vue`（工作台）
- `views/system/user|role|menu/index.vue`（T-0018/T-0019）
- `views/client/index.vue`（T-0028）、`views/apikey/index.vue`（T-0029）
- `views/ratelimit/index.vue`（T-0030，含手动刷新沙箱规则入口）
- `views/session/index.vue`（T-0031，二次确认/默认会话强调/失败保留）
- `views/log/api/index.vue`（T-0032，API 日志 + 沙箱操作日志 + traceId 链路聚合弹窗）
- `views/audit/login|operation/index.vue`（T-0020）
- `views/error/403|404.vue`

## 命令

```
npm install        # 安装依赖
npm run dev        # 本地开发（:5173，代理 /admin-api → :9090）
npm run build      # 生产构建（dist/）
npx vue-tsc --noEmit   # 类型检查
```

## 已知边界（批次6 相关）

- Remember-Me 登录页选项与自动续登（T-0034）未做——后端 `/auth/login` 现契约无 rememberMe 参数。
- 用户列表无删除入口——后端未提供 `/users/{id}` DELETE 端点（软删除+归属转移属批次6 收尾）。
- 系统设置页（T-0041）、客户端统计卡片（T-0035）、会话批量清理（T-0044）、日志导出（T-0045）、
  拖拽排序（T-0039）均为 P1/P2，未在批次5 范围。
