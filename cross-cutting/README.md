# cross-cutting — 三工程共享资料目录

> 对应任务：T-0001（固化独立工程目录与代码隔离边界）、T-0002~T-0006（schema 增量）、T-0007（种子数据）、T-0008（跨工程 ER 对齐）。
> 架构基线：`.cospec/admin/design.md`；任务清单：`.cospec/admin/tasks.md`。

## 1. 仓库一级目录边界（T-0001 定稿）

仓库根目录 `f:/workspaces/python-sandbox/` 采用并列布局，以下一级目录彼此**不存在源码包含、构建嵌入或模块依赖关系**：

| 目录 | 性质 | 说明 |
|------|------|------|
| `admin-web/` | 独立前端工程 | Vue 3 + TypeScript + Vite + Element Plus；产物、源码、配置只落在本目录内（由前端批次初始化） |
| `admin-server/` | 独立后端工程 | Spring Boot 3 + Maven；API 前缀 `/admin-api/**`；端口 9090 |
| `cross-cutting/` | 共享资料（本目录） | 仅承载 schema 增量、种子数据与 ER 对齐资料，**不可被任何工程 import 为代码依赖** |
| `python-sandbox/` | 既有沙箱工程 | 保持 `/api/sandbox/**` 契约；仅在自身结构内做 ApiKey/限流/日志字段改造 |
| `sdk/`、`train/`、`docs-site/` | 既有目录 | 与管理端改造无关 |

## 2. 代码隔离硬边界

1. `admin-server/` **不得** import、复制或直接调用 `python-sandbox/` 下的任何类、常量或工具；两工程 POM 互不嵌套。
2. `admin-server/` 与 `python-sandbox/` 之间**仅允许**两条交互通道：
   - HTTP：`X-Admin-Internal-Token` 保护的 `/internal/**` 内部接口（design.md §6.3、§10.4）；
   - 数据库：对同一 MySQL `sandbox` 库中**既定表**的读写（共库不同表，见下）。
3. `admin-server/` 不直接连接 Docker，不修改 `python-sandbox/` 执行业务。
4. 管理端源码、配置、构建产物和依赖不得落入 `python-sandbox/`、`sdk/` 或 `train/`。
5. 命名空间约定（design.md §3.2）：HTTP 前缀 `/admin-api` vs `/api/sandbox` vs `/internal`；表前缀 `admin_*` / `client_*` / `ratelimit_*` / `sys_*`；Redis Key 前缀 `admin:` vs `sandbox:`。

## 3. 数据库增量与执行顺序（本目录 `database/` 为唯一真相来源）

所有管理端 schema 增量以本目录为准，`admin-server/src/main/resources/db/` 下仅保留副本/引用说明。

目标库：既有 MySQL `sandbox` 库（utf8mb4 / utf8mb4_unicode_ci，蛇形命名）。

**执行顺序**（先执行 python-sandbox 基线，再按编号顺序执行本目录脚本）：

```
1. python-sandbox/src/main/resources/db/init.sql            # 既有基线：api_log、sandbox_operation_log
2. cross-cutting/database/schema/001-admin-rbac.sql         # T-0002 用户/角色/菜单/关联表
3. cross-cutting/database/schema/002-client-apikey.sql      # T-0003 客户端与 ApiKey（哈希/前缀/掩码，明文不存库）
4. cross-cutting/database/schema/003-ratelimit.sql          # T-0004 限流规则
5. cross-cutting/database/schema/004-admin-audit.sql        # T-0005 登录日志与操作日志
6. cross-cutting/database/schema/005-sys-config.sql         # T-0006 系统设置 KV
7. cross-cutting/database/schema/006-sandbox-log-extension.sql  # 扩展既有 api_log / sandbox_operation_log（幂等 ALTER）
8. cross-cutting/database/seed/001-admin-seed.sql           # T-0007 种子数据（幂等，可重复执行）
```

所有脚本设计为幂等（`CREATE TABLE IF NOT EXISTS` / `INSERT IGNORE` / 条件化 ALTER），可重复执行。

## 4. 目录内容

```
cross-cutting/
├── README.md                        # 本文件（边界定稿 + 执行顺序）
└── database/
    ├── er-alignment.md              # T-0008 三工程共库 ER 与归属键对齐
    ├── schema/                      # T-0002~T-0006 + 日志扩展（001~006）
    └── seed/                        # T-0007 初始化种子数据
```

## 5. 后续任务的目标路径约束

后续所有新增、修改任务的合法目标路径仅为 `admin-web/**`、`admin-server/**`、`python-sandbox/**` 或 `cross-cutting/**`；跨目录源码依赖与越界工程改造不纳入任务清单。
