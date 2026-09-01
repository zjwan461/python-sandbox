# admin-server — 管理端后端（独立工程骨架，T-0010）

独立 Spring Boot 3 + Maven 工程，与 `python-sandbox/` 平级、POM 互不嵌套。

## 关键约定

| 项 | 值 |
|----|----|
| API 前缀 | `/admin-api/**`（`server.servlet.context-path`） |
| 端口 | 9090（python-sandbox 为 8080） |
| 数据库 | 共用 MySQL `sandbox` 库（共库不同表，表前缀 `admin_*`/`client_*`/`ratelimit_*`/`sys_*`） |
| Redis | 共用实例，Key 前缀 `admin:`（python-sandbox 为 `sandbox:`） |
| 包结构 | `io.github.sandbox.admin.{auth, rbac, client, apikey, ratelimit, session, log, audit, bridge, common}`（业务包由后续批次落地） |
| 内部凭证 | `admin.internal.token`，ENV `ADMIN_INTERNAL_TOKEN` 覆盖；不进 DB/Redis/日志 |

## 构建

```bash
mvn -s ../python-sandbox/maven-settings.xml compile -f admin-server/pom.xml
```

## 数据库脚本

管理端 schema 增量与种子数据的**唯一真相来源**在 `cross-cutting/database/`
（执行顺序见 [`cross-cutting/README.md`](../cross-cutting/README.md)）。
本工程 `src/main/resources/db/` 不再重复维护 DDL，避免出现两份漂移的 schema。

## 隔离硬约束

- 不 import、复制或直接调用 `python-sandbox/` 下的任何类、常量或工具。
- 与 `python-sandbox/` 仅通过 `/internal/**`（`X-Admin-Internal-Token`）HTTP 或共享数据库表交互。
- 不直接连接 Docker。
