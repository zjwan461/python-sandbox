# 任务清单 - Python Sandbox 管理端

## 工程目录布局示意

- 仓库一级目录采用并列布局：`admin-web/`、`admin-server/`、`cross-cutting/`、`python-sandbox/`、`sdk/`、`train/`、`docs-site/` 等，彼此不存在源码包含、构建嵌入或模块依赖关系。
- `admin-web/` 为独立的 Vue 3 + TypeScript + Vite 工程，`admin-server/` 为独立的管理端后端工程；两者全部产物、源码和配置均只落在各自一级目录内。
- `python-sandbox/` 仅在既有工程结构内完成 ApiKey、限流、活跃会话、内部接口和日志字段改造。
- `cross-cutting/` 仅承载三工程共享的 schema 增量、ER 对齐资料与初始化数据；`admin-server/` 不得 import、复制或直接调用 `python-sandbox/` 下的类、常量或工具。
- 管理端与 `python-sandbox/` 共享同一 MySQL 库时，只能经由 HTTP 或数据库访问既定数据边界；`admin-server/` 不直接连接 Docker，不修改 `python-sandbox/` 执行业务。
- 后续所有新增、修改任务的合法目标路径仅为 `admin-web/**`、`admin-server/**`、`python-sandbox/**` 或 `cross-cutting/**`；跨目录源码依赖与越界工程改造不纳入本清单。

## 阶段5 静态收尾核查记录（本章节为收尾审计，非任务项）

- 核查范围：T-0001~T-0045 全部 45 项。逐项以文件存在性 + 关键类/方法抽查核对状态标记，结果：45 项标记均为 [x] 且与实际代码相符，无错标/漏标，本轮未改动任何任务状态。
- 错误码口径：tasks.md 引用的 11001~11009、12003/12006/12007/12008、20001~20004、30001~30011、40001、429 与 `admin-server/common/exception/ErrorCode.java` 注册值一致；python-sandbox `ApiKeyAuthService` 的 30001~30005 与管理端 ErrorCode 语义两端一致。
- 权限码口径：tasks.md 引用的 user/role/menu/client/apikey/ratelimit/session/apilog/loginlog/oplog/sysconfig/notice 各权限码及 user:export/user:import 均已登记于 `cross-cutting/database/seed/001-admin-seed.sql` 与 `002-admin-batch6.sql`，无缺漏。
- 接口路径口径：design.md §10.3/§10.4 接口清单已按三端实际实现（admin-server controller `@RequestMapping`、admin-web `src/api/*.ts`、python-sandbox `InternalSandboxController`/`HealthController`）对齐修正；§9.6/§11.1 的 `menu/routes` 笔误、§7.6 匿名灰度键名、§4.5 Remember-Me Redis 键、文档头部旧绝对路径（f:/workspaces）等不一致已修正文档。
- 需运行时验证的事项（本轮按用户指示跳过，静态无法证实）：登录失败锁定与验证码一次性消费的时效行为；被踢下线/旧 token 失效实际语义；ApiKey 停用/过期即时拒绝；限流滑动窗口/令牌桶计数精度与 429+Retry-After；Remember-Me 自动续登与滚动过期；会话强销后剩余数回执准确性；`/internal/**` 凭证 constant-time 校验实际拦截；CSV/Excel 导出内容正确性与中文编码。

## 任务 T-0001: 固化独立工程目录与代码隔离边界
- 状态: [x] 已完成（批次1，见 `cross-cutting/README.md`）
- 所属工程: cross-cutting
- 阶段: P0(必须)
- 前置依赖: 无
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): design.md §1.2、§1.3、§3.1、§3.2、§12
- 任务描述: 定稿仓库一级目录边界与模块命名空间，基线目标为 `admin-web/`、`admin-server/`、`cross-cutting/` 与既有 `python-sandbox/` 并列；明确管理端源码、配置、构建产物和依赖不得落入 `python-sandbox/`、`sdk/` 或 `train/`。
- 验收标准: 三工程与共享资料目录的归属清晰；`admin-server/` 与 `python-sandbox/` 仅允许通过 `/internal/**` HTTP 或既有数据库表交互，不允许源码 import，不允许管理端直接依赖 Docker 实现。

## 任务 T-0002: 落地基础 RBAC 与用户部门文本字段 schema
- 状态: [x] 已完成（批次1，`cross-cutting/database/schema/001-admin-rbac.sql`）
- 所属工程: cross-cutting
- 阶段: P0(必须)
- 前置依赖: T-0001
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-USER-01~FR-USER-08、FR-ROLE-01~FR-ROLE-03、FR-MENU-01~FR-MENU-02、FR-DEPT-01~FR-DEPT-03、requirements.md §5.1、design.md §5.1、§5.2
- 任务描述: 在 `cross-cutting/database/` 规划并落地用户、角色、菜单、用户角色关联、角色菜单关联的数据结构与约束方向；用户保留可空部门文本字段，不创建 `admin_dept` 表或部门树实体。
- 验收标准: 用户名、角色权限字符满足唯一约束；用户角色与角色菜单为多对多关系；菜单支持目录、菜单、按钮及树形父级；部门仅是可空文本，不提供本轮部门树 CRUD、岗位挂载或迁移语义。

## 任务 T-0003: 落地客户端与 ApiKey schema
- 状态: [x] 已完成（批次1，`cross-cutting/database/schema/002-client-apikey.sql`）
- 所属工程: cross-cutting
- 阶段: P0(必须)
- 前置依赖: T-0001
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-CLIENT-01~FR-CLIENT-04、FR-APIKEY-01~FR-APIKEY-05、FR-APIKEY-07~FR-APIKEY-08、requirements.md §5.1、§5.3、design.md §6.1、§6.2
- 任务描述: 在 `cross-cutting/database/` 规划并落地 `client_app`、客户端 ApiKey、密钥展示消费状态与校验所需非明文摘要的数据结构；明确客户端编码唯一、归属用户可空、ApiKey 状态和有效时间语义。
- 验收标准: 客户端编码全局唯一；ApiKey 持久化数据不包含可恢复明文且任何列表、详情、Redis 和普通日志均不返回明文；数据库仅保留可安全认证的非明文材料、前缀、后四位掩码与一次性展示消费标记，不新建限流命中独立表。

## 任务 T-0004: 落地限流规则 schema
- 状态: [x] 已完成（批次1，`cross-cutting/database/schema/003-ratelimit.sql` + `006` 对 api_log 的 rate_limit_hit/rate_limit_rule_id 扩展）
- 所属工程: cross-cutting
- 阶段: P0(必须)
- 前置依赖: T-0001
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-RATELIMIT-01~FR-RATELIMIT-04、requirements.md §5.1、design.md §7.1、§7.5
- 任务描述: 在 `cross-cutting/database/` 规划并落地 ApiKey 维度或客户端维度的限流规则数据结构，覆盖窗口、阈值、状态、优先级、有效期和白名单所需标志。
- 验收标准: 规则可唯一确定维度、目标、窗口与状态；支持多条规则作用于同一目标；限流命中明确复用 `api_log` 的 `rate_limit_hit` 与 `rate_limit_rule_id` 字段语义，不创建 `ratelimit_hit_log`。

## 任务 T-0005: 落地登录与操作审计 schema
- 状态: [x] 已完成（批次1，`cross-cutting/database/schema/004-admin-audit.sql`）
- 所属工程: cross-cutting
- 阶段: P0(必须)
- 前置依赖: T-0001
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-AUDIT-01~FR-AUDIT-05、requirements.md §5.1、§5.3、design.md §5.2
- 任务描述: 在 `cross-cutting/database/` 规划并落地登录日志与操作日志数据结构，覆盖操作人、目标主键与对象名、关键字段变更、IP、UA、结果和失败原因。
- 验收标准: 登录日志可表达成功、失败、锁定与原因；操作日志可表达新增、编辑、删除、启停、撤销、重置、强销；两类日志均以追加为业务口径，不向业务侧开放修改或删除能力。

## 任务 T-0006: 落地管理端系统设置 KV schema
- 状态: [x] 已完成（批次1，`cross-cutting/database/schema/005-sys-config.sql`）
- 所属工程: cross-cutting
- 阶段: P0(必须)
- 前置依赖: T-0001
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SYS-01、requirements.md §5.1、design.md §4.5、§7.6
- 任务描述: 在 `cross-cutting/database/` 规划并落地管理端简单键值设置结构，预置“是否允许新注册”、登录失败阈值、锁定时长、“最长免登天数”、默认限流值与匿名调用灰度开关等受控配置项。
- 验收标准: 每个设置项可通过稳定键读取、更新并关联类型与说明；未识别的设置键不被业务接口接受；敏感业务凭证不作为普通系统设置保存。

## 任务 T-0007: 生成管理端初始化种子数据
- 状态: [x] 已完成（批次1，`cross-cutting/database/seed/001-admin-seed.sql`）
- 所属工程: cross-cutting
- 阶段: P0(必须)
- 前置依赖: T-0002, T-0003, T-0005, T-0006
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): requirements.md §4.2、§4.3、§4.4、§4.7、§5.1、design.md §6.2、§13
- 任务描述: 在 `cross-cutting/database/seed/` 生成可重复执行的初始化数据，覆盖唯一超级管理员、超级管理员/管理员/审计员/普通用户默认角色、默认目录菜单按钮权限、默认系统设置和示例 ApiKey 元数据。
- 验收标准: 初始化后可形成超级管理员到角色再到菜单权限的完整链路；普通用户仅拥有自身业务数据范围；示例 ApiKey 仅用于列表识别且为不可用或已消费状态，数据库、初始化文件与执行日志均不出现可调用的明文 ApiKey。

## 任务 T-0008: 对齐跨工程 ER 与 schema 增量方向
- 状态: [x] 已完成（批次1，`cross-cutting/database/er-alignment.md`）
- 所属工程: cross-cutting
- 阶段: P0(必须)
- 前置依赖: T-0002, T-0003, T-0004, T-0005, T-0006
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): requirements.md §5.1、§5.2、§6.1~§6.5、design.md §3.2、§5.1、§8.6
- 任务描述: 在 `cross-cutting/database/` 形成三工程共库 ER 对齐资料，明确管理端表、客户端与限流表、既有 API 日志和沙箱操作日志的字段关联及唯一归属口径。
- 验收标准: ApiKey 关联客户端和可选归属用户；客户端、ApiKey、限流规则、API 日志、沙箱操作日志和运行中会话的归属键一致；ApiLog 的限流命中字段与限流规则主键可关联；`admin-server/` 与 `python-sandbox/` 不通过代码模型共享对象。

## 任务 T-0009: 实现 MyBatis Plus BaseEntity 与自动填充
- 状态: [x] 已完成（批次2，`admin-server/.../common/entity/BaseEntity.java` + `common/handler/AdminMetaObjectHandler.java`，IdType.AUTO 与 schema 对齐，登录前 create_by=system）
- 所属工程: admin-server
- 阶段: P0(必须)
- 前置依赖: T-0002, T-0003, T-0004, T-0005, T-0006
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): design.md §4.1、§4.4
- 任务描述: 在 `admin-server/src/main/java/` 与 `admin-server/src/main/resources/` 建立独立管理端实体的主键、创建/更新时间、创建/更新人和逻辑删除公共能力，并接入当前 Sa-Token 用户上下文与 `system` 初始化口径。
- 验收标准: 所有管理端业务实体可复用统一公共字段；新增、更新、逻辑删除行为一致；登录前和种子数据使用明确系统归属；该能力不修改或继承 `python-sandbox/` 中任何类。

## 任务 T-0010: 初始化 admin-server 独立后端工程
- 状态: [x] 已完成（批次1，`admin-server/pom.xml` + `application.yml` + `AdminServerApplication`，`mvn compile` 验证通过）
- 所属工程: admin-server
- 阶段: P0(必须)
- 前置依赖: T-0001
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): design.md §3.1.2、§4.1
- 任务描述: 在 `admin-server/` 建立独立 Spring Boot 与 Maven 工程，配置 `/admin-api/**` 命名空间、MyBatis Plus、MySQL、Redis、统一应用配置和开发环境配置。
- 验收标准: `admin-server/` 可独立完成管理端后端构建；管理端前缀不占用 `/api/sandbox/**`；工程不嵌入 `python-sandbox/pom.xml`，不跨目录复用其类、常量或工具。

## 任务 T-0011: 初始化 admin-web 并定稿管理端视觉规范
- 状态: [x] 已完成（批次5，`admin-web/` Vite+Vue3+TS+Element Plus+Pinia+Router+Axios 工程：`utils/request.ts` 统一封装 /admin-api 基址、Bearer 短期 token、withCredentials HttpOnly 携带、请求级 X-Trace-Id、401/20001/20002/20003/20004/11004/429/30006 语义化分发；`styles/index.css` 落地 §9.5 视觉规范（#409EFF 主色、8px 卡片圆角、弱阴影、8/12/16/24 间距、斑马纹表格）；`layouts/DefaultLayout.vue`（左菜单+顶栏+面包屑）与 `layouts/BlankLayout.vue`（登录/403/404 样板）；`views/auth/login.vue` 登录页样板；npm install 与 vite build、vue-tsc 均通过）
- 所属工程: admin-web
- 阶段: P0(必须)
- 前置依赖: T-0001
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): design.md §3.1.1、§9.1、§9.3、§9.5、§9.7
- 任务描述: 在 `admin-web/` 建立 Vite + Vue 3 + TypeScript 工程，接入 Element Plus、Pinia、Vue Router 和 Axios，并初始化 API、状态、路由、指令、公共组件、布局、样式与工具目录；在统一请求封装中配置 `/admin-api` 基址、短期 token 注入、HttpOnly Cookie 携带、请求级 `X-Trace-Id` 和 401/403/429 业务语义处理；按设计收敛主色、辅助色、圆角、间距、卡片、字体、弱阴影和表格样式，并形成登录页、Default Layout 与 Blank Layout 视觉样板。
- 验收标准: `admin-web/` 保持根目录一级工程身份；所有管理端请求经 `/admin-api` 和统一 Axios 封装处理，短期会话、HttpOnly Cookie 与 `X-Trace-Id` 的职责不混用；登录页、Default Layout 和 Blank Layout 可直接作为后续业务页面基线；不引入 `python-sandbox/` 前端或后端源码。

## 任务 T-0012: 建立系统设置基础读取能力
- 状态: [x] 已完成（批次2，`sys/service/SysConfigReader.java`：受控键白名单+value_type 强校验+未登记键拒绝+60s 本地缓存+默认业务值回落）
- 所属工程: admin-server
- 阶段: P0(必须)
- 前置依赖: T-0006, T-0007, T-0010
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-AUTH-01、FR-AUTH-03、FR-SYS-01、design.md §4.5、§7.6、§11.1
- 任务描述: 在 `admin-server/` 实现受类型约束的系统设置读取组件及受控配置绑定，优先供登录失败阈值、锁定时长、最长免登天数、默认限流值和匿名调用灰度开关使用。
- 验收标准: 认证与限流组件只读取其允许的系统设置；缺失或非法配置具有明确默认业务值；设置内容不包含客户端 ApiKey 或内部共享凭证；`python-sandbox/` 不依赖此管理端内部配置类。

## 任务 T-0013: 实现图形验证码接口
- 状态: [x] 已完成（批次2，`auth/service/CaptchaService.java` + `GET /auth/captcha`：Easy-Captcha 算术码，Redis `admin:captcha:{id}` TTL 5min，一次性消费，错误不计账号失败次数）
- 所属工程: admin-server
- 阶段: P0(必须)
- 前置依赖: T-0002, T-0010, T-0012
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-AUTH-02、design.md §4.3
- 任务描述: 在 `admin-server/` 实现图形验证码生成、一次性校验、过期处理和失效处理，并提供可供登录页消费的验证码标识与图像内容。
- 验收标准: 验证码错误直接返回验证码业务错误且不增加账号失败次数；验证码校验后不能再次使用；验证码状态存储与登录失败计数分离，命名空间不与 `python-sandbox/` 冲突。

## 任务 T-0014: 实现 Sa-Token 登录态与禁止多端同时在线
- 状态: [x] 已完成（批次2，`common/config/SaTokenConfig.java` 路由拦截+白名单、`application.yml` is-concurrent=false 后登踢先登、`common/security/AdminStpInterface.java` 权限快照、`GlobalExceptionHandler` 20001/20002/20003/20004/11004 语义区分；会话键由 Sa-Token 统一管理，业务自建键 admin: 前缀）
- 所属工程: admin-server
- 阶段: P0(必须)
- 前置依赖: T-0002, T-0010, T-0012
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-AUTH-01、FR-AUTH-04、requirements.md §10.3.6、design.md §4.2、§12、§13
- 任务描述: 在 `admin-server/` 接入 Sa-Token 登录态、短期会话和账号在线唯一映射，配置未登录、角色不足、无权限、账号停用和被踢下线等认证语义。
- 验收标准: 同一账号新会话建立后旧会话被强制失效；后续旧端访问得到被踢下线语义；短期会话、在线映射和 Redis key 位于 `admin:` 命名空间；不使用 Sa-Token 通道承载客户端 ApiKey。

## 任务 T-0015: 实现账号密码登录、验证码接入与踢下线前端闭环
- 状态: [x] 已完成（批次2 admin-server：账密+验证码+失败锁定+登录日志+Sa-Token 签发+首登标记+注销+20004 语义；批次5 admin-web：`views/auth/login.vue` 账号/密码/验证码完整登录页（验证码错误 11001 仅刷新验证码不误报锁定）、`stores/user.ts` 短期 token 以 Pinia 为权威 + sessionStorage 同页镜像（仅用于刷新恢复，标签页关闭即失效，不用 localStorage 等跨会话持久存储）、`utils/request.ts` 20001/20004/11004 统一清态跳登录且不再保留可用会话）
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0011, T-0013, T-0014
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-AUTH-01、FR-AUTH-02、FR-AUTH-04、design.md §4.2、§4.3、§9.3、§11.1
- 任务描述: 在 `admin-server/` 完成账号、密码、验证码、登录失败阈值锁定和退出语义，在 `admin-web/src/views/auth/`、`admin-web/src/stores/` 和 `admin-web/src/router/` 完成登录页接入、按失败阶段加载验证码、短期会话保存和被踢下线处理。
- 验收标准: 登录页完整提供账号、密码与验证码操作；验证码错误不触发锁定，启用提前验证码策略时按登录失败阶段要求输入；连续失败达到配置阈值后账号在锁定期限内不可登录；被踢下线和主动退出均进入登录页且不再保留可用会话；前端不把短期 token 写入其他可被脚本读取的本地存储。

## 任务 T-0016: 实现统一响应、全局异常与参数校验
- 状态: [x] 已完成（批次2，`common/result/R.java` + `PageResult.java`、`common/exception/ErrorCode.java`（§10.2 分段）+ `BusinessException.java` + `GlobalExceptionHandler.java`、`SaTokenConfig` 统一 CORS、jakarta validation 字段级错误透出；`common/filter/AdminTraceFilter.java` traceId 独立实现）
- 所属工程: admin-server
- 阶段: P0(必须)
- 前置依赖: T-0010
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): design.md §4.6、§4.7、§10.1、§10.2
- 任务描述: 在 `admin-server/` 建立 `/admin-api/**` 统一响应包装、业务异常、认证授权异常、参数校验错误和兜底异常处理，并统一 CORS 与 Axios 可识别的业务语义。
- 验收标准: 所有管理端接口使用同一成功、失败、分页与时间表达；业务异常不暴露内部对象；校验错误能定位字段；未登录、无权限、角色不足、停用与被踢下线具有互不混淆的语义。

## 任务 T-0017: 实现登录用户密码生命周期管理
- 状态: [x] 已完成（批次2 admin-server：PUT /auth/password 校验旧密码+成功后作废旧会话+first_login 解除；管理员重置/解锁/启停端点齐备。批次5 admin-web：`views/auth/change-password.vue` 改密页（forced=1 首登强制改密不可跳过，路由守卫拦截其他页面；11005/11006 语义透出；成功后清态回登录）、`views/system/user/index.vue` 管理员重置密码与锁定状态展示+手动解锁入口（列表显示 locked 标签）、个人中心 `views/auth/profile.vue`；全链路不展示密码或摘要）
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0015, T-0016
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-AUTH-05~FR-AUTH-07、design.md §11.1
- 任务描述: 在 `admin-server/` 与 `admin-web/` 实现登录用户自身密码修改、修改后旧令牌作废、首次登录强制改密状态以及管理员重置密码和手动解锁所需的业务联动。
- 验收标准: 修改密码后所有未失效旧会话立即作废；首次登录用户不能跳过改密流程；管理员可重置任意账号密码并能按 FR-AUTH-05 语义手动解锁；前端提供改密、重置和锁定状态表达，不展示密码或密码摘要。

## 任务 T-0018: 实现用户管理后端与前端
- 状态: [x] 已完成（批次2/5 既有范围不变；批次6 补齐删除：admin-server `UserController` 新增 `DELETE /users/{id}` 与 `DELETE /users/batch`（user:delete + @OperationLog），`AdminUserService.delete` 落实业务规则阻止（当前登录账号 12008、最后超管 12007、已在线会话 12006、仍绑定有效 ApiKey 12006）+ 软删除（deleted=1、status=0、作废旧 Sa-Token 会话与 Remember-Me 长期 token）+ 历史归属转移（client_app.owner_user_id / client_api_key.bound_user_id 批量改写为操作管理员，日志按归属键天然随行、不悬空，软删后仅 ALL 域可见）；admin-web 用户页挂删除按钮（行内单删+勾选批删，二次确认提示归属转移语义，当前登录行不可选））
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0002, T-0009, T-0010, T-0011, T-0015, T-0016, T-0017
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-USER-01~FR-USER-06、FR-USER-08、FR-RBAC-04、requirements.md §10.3.8、design.md §5.5、§10.3
- 任务描述: 在 `admin-server/src/main/java/` 与 `admin-web/src/views/system/user/`、`admin-web/src/api/` 实现用户分页列表、多条件筛选、字段排序、新增、编辑、单删与批量删除、启停、角色绑定、重置密码、锁定标记及个人中心；用户部门使用可空文本，并落实普通用户软删除后的历史数据归属转移。
- 验收标准: FR-USER-01~FR-USER-06 与 FR-USER-08 的字段和动作完整可用；已登录或仍持有效 ApiKey 的用户删除按业务规则阻止；可删除的普通用户停用或软删除后，其历史数据仅管理员和审计员可见且不存在悬空归属；停用用户及其 ApiKey 在 `python-sandbox/` 侧形成明确拒绝语义；普通用户不能访问他人用户管理数据或管理动作，越权请求不返回目标数据。

## 任务 T-0019: 实现角色与菜单管理后端和前端
- 状态: [x] 已完成（批次2 admin-server：角色列表/增改删/启停/分配菜单 + 内置保护/引用阻断/routes；批次5 admin-web：`views/system/role/index.vue` 角色分页、新增/编辑（内置角色权限字符禁改、删除按钮仅非内置显示）、启停、el-tree 分配菜单（含半选父节点提交）；`views/system/menu/index.vue` 菜单树表格 CRUD（M/C/F 三类、按钮必填 perms 前端同步校验、上级选择器、路由/组件/外链/缓存/显隐字段维护）；当前用户仅获自身授权菜单与按钮）
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0002, T-0009, T-0010, T-0011, T-0016
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-ROLE-01~FR-ROLE-03、FR-MENU-01~FR-MENU-02、design.md §5.2、§5.3、§10.3
- 任务描述: 在 `admin-server/src/main/java/` 与 `admin-web/src/views/system/role/`、`admin-web/src/views/system/menu/` 实现角色列表、增改删、状态、菜单和按钮权限分配，以及菜单树增改删、按钮权限标识维护和当前用户可访问菜单路由数据。
- 验收标准: 角色权限字符唯一；用户至少可关联一个角色；菜单树可表达目录、菜单、按钮、路由、组件、外链、缓存和显隐；按钮权限采用统一权限字符；当前用户只能得到自身角色授权的菜单与按钮数据。

## 任务 T-0020: 实现管理端审计日志落库与查询
- 状态: [x] 已完成（批次2 admin-server：审计实体/登录落库/写操作切面/只读查询端点，权限码 loginlog:view、oplog:view；批次5 admin-web：`views/audit/login/index.vue` 登录日志筛选（用户名/结果/时间范围）+分页列表（成功/失败/锁定标签+原因+IP/UA）、`views/audit/operation/index.vue` 操作日志筛选（模块/类型/操作人/目标ID/时间）+列表+详情对话框（变更摘要 JSON、traceId 可跳链路详情）；页面组件路径与种子菜单 audit/login/index、audit/operation/index 对齐，普通用户无审计菜单授权即无入口且后端独立拒绝）
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0005, T-0009, T-0010, T-0015, T-0016
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-AUDIT-01~FR-AUDIT-05、FR-APIKEY-08、FR-RBAC-04、design.md §4.1、§5.2、§6.2
- 任务描述: 在 `admin-server/` 建立统一登录日志和写操作审计能力及服务接入点，在 `admin-web/src/views/audit/` 提供管理员与审计员可用的登录日志、操作日志筛选、列表和详情页面。
- 验收标准: 登录成功、失败、锁定及原因完整留痕；管理端新增、编辑、删除、启停、撤销、重置、强销和对接调用均记录当前操作人、对象主键与对象名、关键变更、IP、UA 和结果；审计记录只追加；普通用户无查看权限，越权访问不能获得日志数据。

## 任务 T-0021: 实现 MyBatis 管理端数据权限拦截器
- 状态: [x] 已完成（批次2 通用机制，`common/datapermission/AdminDataPermissionHandler`（MyBatis Plus MultiDataPermissionHandler，注册表驱动：client_app/client_api_key/api_log/sandbox_operation_log 的 SELF 行过滤，COALESCE(bound_user_id, client_app.owner_user_id) 口径）+ `DataPermissionIgnoreHolder`（runIgnored 绕过）+ `MybatisPlusConfig` 插件链装配；superadmin/admin/auditor=ALL 不过滤，common=SELF；admin_*/sys_config/ratelimit_rule 等元数据表不受作用。批次3 的客户端/ApiKey/日志/会话查询直接复用）
- 所属工程: admin-server
- 阶段: P0(必须)
- 前置依赖: T-0018, T-0019
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-RBAC-01~FR-RBAC-04、requirements.md §9、design.md §5.4、§5.5
- 任务描述: 在 `admin-server/` 建立基于 MyBatis 的集中数据权限入口，按当前用户角色解析全部、本人或受限可见域，并限定作用于客户端、ApiKey、API 日志、沙箱操作日志和运行中会话查询。
- 验收标准: 管理员和审计员使用全部可见域；普通用户仅得到客户端归属用户或 ApiKey 绑定用户为自己的记录；越权请求不返回业务数据且能向审计能力提供明确事件；管理端元数据表不被本任务的数据权限规则误作用。

## 任务 T-0022: 扩展 python-sandbox 既有 ApiLog 与沙箱操作日志 schema
- 状态: [x] 已完成（批次4，`entity/ApiLog.java` 增加 clientId/apiKeyId/ownerUserId/rateLimitHit/rateLimitRuleId，`entity/SandboxOperationLog.java` 增加 clientId/apiKeyId/ownerUserId；`resources/db/init.sql` 同步扩展列与索引（幂等 ALTER 见 cross-cutting 006 脚本）；`ApiLogAspect`/`SandboxOperationLogAspect` 从 AuthContext 线程上下文填充归属与限流命中字段，无上下文时保持 NULL，既有列与写入语义不变）
- 所属工程: python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0003
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-LOG-01~FR-LOG-03、FR-RBAC-03、requirements.md §5.2、design.md §8.5、§8.6、§12
- 任务描述: 仅在 `python-sandbox/src/main/java/io/github/sandbox/entity/`、`python-sandbox/src/main/resources/` 与既有 Mapper/Aspect 边界内，为 `api_log` 增加客户端、ApiKey、归属用户、限流命中与命中规则字段，为 `sandbox_operation_log` 增加相同归属字段，并修改 `ApiLogAspect`、`SandboxOperationLogAspect` 从请求与限流上下文填充这些新增字段。
- 验收标准: 既有列和既有写入语义不破坏；新字段在无鉴权上下文时允许为空；ApiLog 的限流命中与规则标识可被查询；操作日志可通过相同归属字段关联 ApiKey 和客户端；不复制 `admin-server/` 的实体或配置。

## 任务 T-0023: 实现 python-sandbox ApiKey 校验过滤器与上下文传递
- 状态: [x] 已完成（批次4，`service/ApiKeyAuthService.java`（明文→SHA-256 hex 小写按 client_api_key.key_hash 查表，区分 30001 API_KEY_MISSING / 30002 API_KEY_NOT_FOUND / 30003 API_KEY_INVALID（停用/撤销/过期/未生效）/ 30004 CLIENT_DISABLED / 30005 USER_DISABLED；归属口径 COALESCE(bound_user_id, client_app.owner_user_id)）+ `interceptor/ApiKeyAuthInterceptor.java`（校验通过后写 `context/AuthContext` 线程上下文供限流与 Aspect 使用；匿名灰度读 sys_config ratelimit.anonymous.allowed 默认 false，开启时匿名放行仍受全局默认限流）+ `WebConfig.java` 替换原静态单 key apiKeyInterceptor，仅作用 /api/sandbox/**，/internal/** 不进入该通道；新增只读实体 ClientApp/ClientApiKey/AdminUserLite 与对应 Mapper）
- 所属工程: python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0003, T-0022
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-CLIENT-03~FR-CLIENT-04、FR-APIKEY-05、FR-LOG-01、requirements.md §6.1、design.md §8.1、§8.2
- 任务描述: 仅在 `python-sandbox/src/main/java/io/github/sandbox/filter/` 与既有 Web 配置边界内实现所有 `/api/sandbox/**` 请求的 ApiKey 校验，区分缺失、未知、停用、撤销、过期、未生效、客户端停用和归属用户停用语义，并向后续限流与日志组件传递客户端、ApiKey 和归属用户上下文。
- 验收标准: 匿名灰度关闭时缺失或无效 ApiKey 明确拒绝；灰度开启时允许匿名调用但仍应用全局默认限流；校验通过后 traceId、ApiKey、客户端、用户上下文可供现有 Aspect 使用；用户、客户端、ApiKey 任一停用均拒绝对应调用；内部 `/internal/**` 不进入该 ApiKey 鉴权通道。

## 任务 T-0024: 实现 python-sandbox 限流拉取组件与本地判定
- 状态: [x] 已完成（批次4，`service/RatelimitService.java`：启动加载 + @Scheduled 定时拉取（sandbox.ratelimit.refresh-interval-millis 默认 60s）ratelimit_rule 到本地缓存（拉取条件 status=1 AND effective_time<=NOW() AND (expire_time IS NULL OR expire_time>NOW())，deleted 由逻辑删除附加）；判定顺序 白名单 rate_limit_exempt=1 跳过全部 → API_KEY 滑动窗口 → CLIENT 令牌桶 → GLOBAL（dimension=GLOBAL target_id=0 规则 + sys_config ratelimit.default.minute/hour/day 默认值，非法回落 60/1000/10000）；多规则叠加任一命中即拒绝；命中经 ApiKeyAuthInterceptor 写 api_log.rate_limit_hit=1+rate_limit_rule_id 并返回 HTTP 429（含 Retry-After）；拉取失败保留旧缓存置 stale 标志（响应头 X-Ratelimit-Config-Stale），reload() 供内部接口触发；不依赖 admin-server 类）
- 所属工程: python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0004, T-0023
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-RATELIMIT-01~FR-RATELIMIT-04、requirements.md §6.2、design.md §7.2~§7.5、§8.1、§8.3
- 任务描述: 仅在 `python-sandbox/src/main/java/io/github/sandbox/` 与 `python-sandbox/src/main/resources/` 实现启动加载和定时从 MySQL 拉取限流规则到本地缓存，按 ApiKey 滑动窗口、客户端令牌桶及全局默认规则执行判定并处理白名单。
- 验收标准: 规则在启动后进入本地缓存并可按配置周期刷新；同一目标多规则命中任一即拒绝；白名单 ApiKey 跳过全部规则；ApiKey 限流命中写入扩展 ApiLog 字段并通过明确限流响应表达；拉取失败保留旧缓存并使当前调用获得“配置未最新”的明确业务语义；不依赖 `admin-server/` 的 Java 类或内部网关。

## 任务 T-0025: 实现 python-sandbox 活跃会话接口
- 状态: [x] 已完成（批次4，`service/SandboxService.java`：SandboxSession 登记 createTime 与 ownerClientId/ownerApiKeyId/ownerUserId（创建容器时从 AuthContext 捕获，无鉴权上下文为 NULL 不伪造）；新增 listSessionSnapshots()/findSessionSnapshot()（仅枚举当前进程内 ConcurrentHashMap 快照，孤儿会话不伪造）与 destroySession()（回执剩余会话数）；新增 SessionSnapshot DTO；既有 /api/sandbox/** 执行接口契约不变，DELETE /api/sandbox/session/{sessionId} 强销保持可用）
- 所属工程: python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0003, T-0023
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SESSION-01、requirements.md §6.3、design.md §8.4、§10.4
- 任务描述: 仅在 `python-sandbox/src/main/java/io/github/sandbox/controller/`、`python-sandbox/src/main/java/io/github/sandbox/service/` 增加从内存会话快照生成活跃会话列表和详情关联数据的能力，保留 sessionId、containerId、容器名、创建时间、最后活跃时间、默认标记和客户端/ApiKey/用户归属。
- 验收标准: 返回内容只来自当前进程内活跃会话；会话所属信息与 ApiKey 调用上下文一致；服务重启后无法枚举的孤儿会话不被伪造；既有强销能力保持可用且不改变 `/api/sandbox/**` 执行接口契约。

## 任务 T-0026: 实现 python-sandbox 内部接口与内部凭证校验
- 状态: [x] 已完成（批次4，`interceptor/InternalTokenInterceptor.java`：/internal/** 统一校验 X-Admin-Internal-Token，配置键 sandbox.internal.token（ENV ADMIN_INTERNAL_TOKEN 覆盖，不入库），constant-time 比对，缺失/错误/未配置一律 401 INTERNAL_UNAUTHORIZED，与客户端 ApiKey 通道完全分离；`controller/InternalSandboxController.java` 按 Bridge 契约输出：GET /internal/sandbox/sessions→{"sessions":[...]}（时间 yyyy-MM-dd HH:mm:ss 字符串）、GET /sessions/{id}/detail→{"session":{...}}（不存在 404）、DELETE /sessions/{id}→{"success","message","remainingSessions"}（失败不抛异常原样回执）、POST /ratelimit/reload→{"success"}；WebConfig 注册 /internal/** 独立拦截器；application.yml/.env.example 增加 sandbox.internal.token 与 sandbox.ratelimit.* 配置）
- 所属工程: python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0010, T-0024, T-0025
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): requirements.md §6.3、§6.5、design.md §6.3、§10.4、§13
- 任务描述: 仅在 `python-sandbox/src/main/java/` 与 `python-sandbox/src/main/resources/application.yml` 增加 `/internal/**` 边界、独立 `X-Admin-Internal-Token` 校验、活跃会话列表/详情/强销和按需刷新限流规则的内部能力；凭证支持环境变量覆盖且不写入数据库。
- 验收标准: 内部凭证与客户端 ApiKey 完全分离；缺失或错误内部凭证返回明确未授权语义；只有管理端内部链路可调用会话与限流刷新能力；内部接口不暴露沙箱执行细节；强销回执表达成功/失败及剩余会话数。

## 任务 T-0027: 实现 admin-server 对接 python-sandbox 的内部调用封装
- 状态: [x] 已完成（批次3，`admin-server/.../bridge/client/SandboxBridgeClient.java` + `bridge/config/SandboxBridgeProperties.java` + dto（SandboxSessionVO/SessionDestroyResultVO）：统一 RestTemplate 封装 /internal/sandbox/**、X-Admin-Internal-Token 由配置注入（ENV ADMIN_INTERNAL_TOKEN 覆盖）、401/403→SANDBOX_BRIDGE_UNAUTHORIZED、404→SESSION_NOT_FOUND、不可达→SANDBOX_BRIDGE_ERROR；业务 Controller 不硬编码 URL 或凭证；不导入 python-sandbox 代码。python-sandbox 侧契约实现见 T-0026）
- 所属工程: admin-server
- 阶段: P0(必须)
- 前置依赖: T-0010, T-0012, T-0026
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): requirements.md §5.3、§6.3、§6.5、design.md §1.3、§6.3、§10.4
- 任务描述: 在 `admin-server/src/main/java/io/github/sandbox/admin/bridge/` 建立统一 WebClient 或 RestTemplate 客户端，封装 `/internal/**` 调用、从 `admin-server/src/main/resources/application.yml` 读取可由 ENV 覆盖的 `X-Admin-Internal-Token` 并统一注入请求、处理内部错误和返回会话与限流刷新语义。
- 验收标准: 管理端业务 Controller 不直接硬编码 `python-sandbox/` URL 或凭证；每次内部调用只使用管理端配置中的同一内部凭证；客户端 ApiKey 不会发送到该网关；内部调用失败转换为管理端明确业务错误；该模块不导入 `python-sandbox/` 代码。

## 任务 T-0028: 实现客户端管理后端与前端
- 状态: [x] 已完成（批次3 admin-server：分页筛选/详情/增改（编码唯一、普通用户归属强制自身）/启停（CLIENT_DISABLED 语义）/删除阻断（30008）+ @OperationLog + SELF 行过滤；批次5 admin-web：`views/client/index.vue`（与种子菜单 component=client/index 对齐）名称/编码/状态筛选分页列表、新增/编辑对话框（编码唯一且创建后不可改、ALL 域可填归属用户ID、普通用户归属由后端强制自身）、启停用二次确认（提示停用即刻令其启用 ApiKey 被沙箱拒绝）、删除确认（提示持有有效密钥将被阻断，30008 message 由拦截器透出）；按钮 v-permission client:add/edit/disable/delete）
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0003, T-0009, T-0010, T-0011, T-0016, T-0020, T-0021
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-CLIENT-01~FR-CLIENT-04、FR-RBAC-02~FR-RBAC-04、design.md §6.1、§10.3
- 任务描述: 在 `admin-server/` 实现客户端分页列表、名称/编码/归属人/状态筛选、新增编辑、启停、删除前 ApiKey 处理和归属权限，在 `admin-web/src/views/client/` 实现对应页面、表单和状态操作。
- 验收标准: 客户端编码唯一；普通用户只能管理自己的客户端；停用客户端后其名下启用 ApiKey 被 `python-sandbox/` 拒绝；删除仍持有有效 ApiKey 的客户端前必须给出处理提示且完成阻断；管理员和审计员的查看范围符合 FR-RBAC-01。

## 任务 T-0029: 实现 ApiKey 管理后端与前端
- 状态: [x] 已完成（批次3 admin-server：摘要存储+一次性明文+状态机+惰性过期+全维度筛选+审计；批次5 admin-web：`views/apikey/index.vue`（component=apikey/index 对齐）名称/客户端/状态筛选分页、列表仅 `前缀****后四位` 掩码、创建/编辑对话框（客户端必选、绑定用户可空、生效/过期时间、白名单开关）、创建与重新生成后弹出一次性明文对话框（可复制、关闭即丢弃、不写入任何存储/URL/日志）、启停/撤销（红色高危二次确认，不可逆，已撤销行为终态仅展示）/重新生成入口，按钮 v-permission apikey:add/edit/disable/revoke/reset）
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0003, T-0009, T-0010, T-0011, T-0016, T-0020, T-0021, T-0023, T-0028
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-APIKEY-01~FR-APIKEY-08、FR-RBAC-02~FR-RBAC-04、design.md §6.2、§11.2
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/apikey/` 实现 ApiKey 创建、客户端与可选用户绑定、生效/过期时间、状态、撤销、重新生成、列表筛选、一次性明文展示、复制与重新生成入口。
- 验收标准: 创建响应只允许携带一次明文；一次性展示期内可复制且后续列表和详情只显示前缀与后四位掩码；重新生成会替换旧凭证并撤销旧 ApiKey；撤销不可逆；未生效、过期、停用和撤销状态可筛选并由 `python-sandbox/` 拒绝；创建、启停、撤销和重新生成均进入 `admin_op_log`。

## 任务 T-0030: 实现限流规则管理后端与前端
- 状态: [x] 已完成（批次3 admin-server：维度/窗口/阈值校验+目标归属校验+30010 冲突+保存后经 Bridge 触发重拉+手动 reload（module=bridge 审计）；批次5 admin-web：`views/ratelimit/index.vue`（component=ratelimit/index 对齐）维度/窗口/状态筛选分页列表（目标列以 ApiKey 掩码/客户端编码友好展示，含优先级/状态/有效期）、新增/编辑对话框（维度单选联动目标选择器：API_KEY→ApiKey 下拉、CLIENT→客户端下拉、GLOBAL→固定0 仅管理员可见语义，正整数阈值+有效期）、启停/删除、"手动刷新沙箱规则"按钮（POST /ratelimits/reload，success=false 时提示定时拉取兜底）；30010/30011 message 经拦截器透出）
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0004, T-0009, T-0010, T-0011, T-0016, T-0020, T-0021, T-0024, T-0028, T-0029
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-RATELIMIT-01~FR-RATELIMIT-04、FR-RATELIMIT-03、design.md §7.1、§7.3、§7.4
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/ratelimit/` 实现按 ApiKey 或客户端配置规则、分页列表、分钟/小时/天窗口、正整数阈值、优先级、状态、有效期及保存后触发 `python-sandbox/` 刷新本地规则的业务入口。
- 验收标准: 规则维度与目标类型一致；目标不存在、阈值非法或状态冲突时不能保存；启用规则在启动或定时拉取后进入 `python-sandbox/`；前端可查看规则目标、状态与优先级；规则保存与刷新动作有明确管理端审计记录。

## 任务 T-0031: 实现运行中会话列表、详情与强制销毁闭环
- 状态: [x] 已完成（批次3 admin-server：Bridge 列表/详情/强销+SELF 过滤+富化回填+30001~40001 语义+审计；批次4 python-sandbox：内部会话快照与强销接口（T-0025/T-0026）；批次5 admin-web：`views/session/index.vue`（component=session/index 对齐）展示 sessionId/容器/创建与最后活跃时间/客户端编码/ApiKey 标签/归属用户/默认标记（普通用户仅本人数据由后端过滤），默认会话红色强调标签+专属"仅默认会话"筛选、不活跃分钟数筛选，销毁经二次确认（默认会话用高危文案），回执 success=false 时不移除行且原样展示失败原因与剩余会话数、成功才刷新列表，session:force 按钮经 v-permission 且后端独立校验）
- 所属工程: admin-server | admin-web | python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0020, T-0021, T-0025, T-0026, T-0027, T-0028
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SESSION-01、FR-SESSION-04、FR-RBAC-01~FR-RBAC-04、requirements.md §5.3、design.md §8.4、§10.4、§11.4
- 任务描述: 在 `python-sandbox/` 沿用并增强内部强销语义，在 `admin-server/` 封装列表与强销业务接口，在 `admin-web/src/views/session/` 实现活跃会话列表、普通用户本人数据范围、管理员二次确认、默认会话强调和销毁结果回写。
- 验收标准: 列表展示 sessionId、containerId、容器名、创建/最后活跃时间、ApiKey、客户端、用户和默认标记；普通用户不显示他人会话；管理员确认后调用内部强销入口；销毁失败不从前端移除记录且保留失败结果；成功销毁后列表语义反映会话已不存在并写入操作审计。

## 任务 T-0032: 实现调用记录查询与 traceId 链路详情
- 状态: [x] 已完成（批次3 admin-server：两表只读分页筛选+排序+详情+traceId 聚合+*Truncated 标记+SELF/ALL 数据范围+归属富化；批次5 admin-web：`views/log/api/index.vue`（component=log/api/index 对齐）双页签——API 日志（时间范围/客户端/ApiKey/方法/路径/状态码/traceId/IP/会话ID/限流命中筛选，createdAt/状态码/耗时列 sortable 自定义排序）、沙箱操作日志（时间/类型/结果/traceId/会话筛选），行详情弹窗展示请求参数/stdout/stderr/exitCode/errorMessage 且 *Truncated=true 处渲染"已截断（非完整数据）"标签；traceId 列点击打开链路聚合弹窗（API 日志+按序操作日志同屏，各自可下钻详情），亦支持 /business/apilog?traceId= 外部跳入）
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0009, T-0010, T-0011, T-0016, T-0020, T-0021, T-0022, T-0023
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-LOG-01~FR-LOG-05、FR-LOG-07、FR-RBAC-01~FR-RBAC-03、design.md §5.5、§8.5、§10.3
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/log/` 实现 API 日志和沙箱操作日志的筛选分页、默认时间倒序、自定义排序、详情及按 traceId 聚合链路，输出请求参数、耗时、标准输出、标准错误、退出码、错误信息和截断标记。
- 验收标准: 筛选覆盖需求列出的时间、ApiKey、客户端、用户、方法、路径、状态码、traceId、IP、类型和结果；普通用户只获得本人可见域；管理员和审计员获得全部记录；同 traceId 的 API 日志与多次操作可同屏查看；截断内容明确标记且不伪装为完整内容。

## 任务 T-0033: 实现前端 v-permission 按钮权限指令
- 状态: [x] 已完成（批次5，`src/directives/permission.ts` 全局指令：默认任意匹配、`v-permission:all` 全部匹配、`*:*:*` 超管放行；不匹配时 mounted 钩子直接移除 DOM 节点（不保留 display:none）；权限码由 `stores/user.ts` 自 `GET /auth/whoami` 的 permissions 集合接入；已接入用户/角色/菜单/客户端/ApiKey/限流/会话强销各管理动作；后端 @SaCheckPermission 独立校验，隐藏不可绕过）
- 所属工程: admin-web
- 阶段: P0(必须)
- 前置依赖: T-0011, T-0019
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-MENU-02、design.md §5.3、§9.4
- 任务描述: 在 `admin-web/src/directives/` 与 `admin-web/src/stores/` 实现全局 `v-permission` 指令、权限码状态接入和任意匹配/全部匹配语义，并接入角色、菜单、ApiKey、限流、审计及会话等管理动作。
- 验收标准: 具备权限时显示对应操作控件；不匹配时控件不保留在可见 DOM；支持需求约定的任意或全部匹配；后端权限校验仍独立存在，隐藏控件不能绕过服务端授权。

## 任务 T-0034: 完成 Remember-Me 前后端闭环
- 状态: [x] 已完成（批次6 admin-server：`auth/service/RememberMeService.java`（Redis `admin:remember:token:{token}`→userId + `admin:remember:user:{userId}` 反向索引，TTL=sys_config remember.me.max.days，轮换作废旧 token，HttpOnly Cookie `admin_remember`，脚本不可读）+ `LoginRequest.rememberMe`（未勾选不签发且清残留 Cookie）+ `POST /auth/auto-login`（白名单免登录，凭 Cookie 重建 Sa-Token 短期会话+权限快照+登录日志 REMEMBER_ME+滚动续期；账号停用/删除即失效）+ 注销/改密/重置/停用联动 `revoke`；admin-web：登录页"记住我"复选框、`stores/user.ts` `tryAutoLogin`（长期 token 全程不进 Pinia/sessionStorage/localStorage）、进入登录页自动尝试续登成功即直跳目标页；客户端 ApiKey 不进入本通道）
- 所属工程: admin-server | admin-web
- 阶段: P1(重要)
- 前置依赖: T-0006, T-0007, T-0009, T-0010, T-0013, T-0015, T-0016
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-AUTH-03、FR-AUTH-04、design.md §4.2、§9.3、§11.1
- 任务描述: 在 `admin-server/` 实现与短期 token 分离的 Remember-Me 长 token 签发、续登、过期和 HttpOnly Cookie 通道，在 `admin-web/src/views/auth/` 接入记住我选项、自动续登、主动退出与被踢下线后的长 token 失效。
- 验收标准: 勾选记住我后浏览器通过 HttpOnly Cookie 自动携带长 token；未勾选时不签发长 token；超过“最长免登天数”或主动退出后长 token 立即失效；前端不把长期 token 写入 Pinia 或其他可被脚本读取的本地存储；客户端 ApiKey 不进入 Remember-Me 通道。

## 任务 T-0035: 实现客户端统计卡片与归属转移
- 状态: [x] 已完成（批次6 admin-server：`client/dto/ClientStatsVO.java` + `ClientAppService.stats`（ApiKey 总数/活跃数（启用+停用且未过期）/今日调用/累计调用，按 client_id 聚合与筛选范围一致，先经 detail() 可见域校验）+ `transferOwner`（仅 ALL 域，目标用户存在性与同归属校验，client_app.owner_user_id 改写并同步 api_log/sandbox_operation_log 归属快照改到新主人——转移后历史记录按新归属展示）+ `GET /clients/{id}/stats`、`PUT /clients/{id}/owner`（client:edit + @OperationLog 审计）；admin-web：客户端页"统计"弹窗四卡片 + "转移归属"入口（仅 ALL 域显示），普通用户无入口且后端独立拒绝越权转移）
- 所属工程: admin-server | admin-web
- 阶段: P1(重要)
- 前置依赖: T-0028, T-0029, T-0032
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-CLIENT-05~FR-CLIENT-06、design.md §10.3
- 任务描述: 在 `admin-server/` 聚合客户端 ApiKey 数量、活跃 ApiKey 数量、今日调用次数和累计调用次数，并提供有权限的客户端归属转移；在 `admin-web/src/views/client/` 展示统计卡片和转移操作。
- 验收标准: 统计口径与当前客户端筛选范围一致；归属转移成功后该客户端及历史调用记录按新归属用户展示；普通用户不能转移他人客户端；越权转移被拒绝并进入管理端审计。

## 任务 T-0036: 实现多规则叠加、白名单与全局默认限流
- 状态: [x] 已完成（批次6 收尾核查：三端能力在批次3/4 已实装并经本轮复核——python-sandbox `RatelimitService` 判定顺序 白名单 rate_limit_exempt=1 跳过全部 → API_KEY 滑动窗口多规则叠加任一命中即拒 → CLIENT 令牌桶 → GLOBAL（dimension=GLOBAL 规则 + sys_config ratelimit.default.minute/hour/day 回落 60/1000/10000）；admin-server `ApiKeyUpsertRequest/VO.rateLimitExempt` 白名单标志维护（apikey:edit）与 `RatelimitRuleService` 多规则目标归属校验（普通用户仅自身可见域目标、GLOBAL 仅 ALL 域）；admin-web apikey 页白名单开关 + ratelimit 页多规则/优先级/全局维度已备；命中写 api_log.rate_limit_hit + rule_id 并 429，审计与查询链路复用既有能力，本批无契约变更）
- 所属工程: admin-server | admin-web | python-sandbox
- 阶段: P1(重要)
- 前置依赖: T-0024, T-0029, T-0030
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-RATELIMIT-02、FR-RATELIMIT-05~FR-RATELIMIT-06、design.md §7.3、§7.4、§7.6
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/ratelimit/` 增加 ApiKey 无限流标志、多个规则叠加和全局默认规则配置，在 `python-sandbox/` 按 ApiKey、客户端、全局默认优先级完成对应判定。
- 验收标准: 同一目标可叠加多条规则且任一命中即拒绝；白名单 ApiKey 跳过全部限流；未匹配任何专属规则的调用方受全局默认规则约束；普通用户不能修改不属于自己的目标规则；相关配置和命中结果符合既有审计与查询要求。

## 任务 T-0037: 实现会话筛选、关联日志详情与默认会话保护
- 状态: [x] 已完成（批次6 admin-server：`SessionQuery` 扩展 sessionId 模糊 + createdBegin/createdEnd 创建时间范围（连同既有 ApiKey/客户端/用户/不活跃分钟/仅默认形成完整筛选，各筛选仅返回匹配会话）+ `GET /sessions/{id}/logs`（`SessionRelatedLogsVO`：先经 detail() 可见域校验越权 40001，再经 `LogQueryService.recentBySession` 取最近 API 日志与操作日志各 N 条，两表 SELF 行过滤天然生效）；python-sandbox 内部会话数据边界不变（T-0025/0026 契约复用）；admin-web 会话页筛选区扩展（会话ID/创建时间范围）+ 详情弹窗内联"最近 API 日志/最近操作日志"双页签；默认会话销毁保留高危显式二次确认（type=error 标题"默认会话销毁 - 高危确认"），非强销权限用户经 v-permission 无销毁入口且后端 session:force 独立拒绝）
- 所属工程: admin-server | admin-web | python-sandbox
- 阶段: P1(重要)
- 前置依赖: T-0025, T-0031, T-0032
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SESSION-02~FR-SESSION-03、FR-SESSION-05、design.md §8.4、§10.3、§10.4
- 任务描述: 在 `admin-server/`、现有 `python-sandbox/` 内部会话数据边界和 `admin-web/src/views/session/` 增加 ApiKey、客户端、用户、活跃时间与默认会话筛选，关联最近 API 日志和沙箱操作日志，并强化默认会话销毁确认语义。
- 验收标准: 每种筛选只返回匹配活跃会话；详情可查看该会话关联日志并遵守数据权限；默认会话销毁必须在管理员显式二次确认后执行；其他用户无强销入口。

## 任务 T-0038: 实现基于后端菜单的动态路由加载
- 状态: [x] 已完成（后端 routes 端点批次2 已备（T-0019）；批次5 admin-web：`router/index.ts` 静态登录/403/404/改密/个人中心路由 + beforeEach 守卫（未登录→/login 带 redirect、已登录访问 /login→/、meta.perms 未命中→/403、动态路由未加载先加载并重放导航、initializing 标志防并发重复注册）；`router/dynamic.ts` 按 /menus/routes 树递归构建（import.meta.glob 解析 views 组件，未匹配组件跳过告警）挂入 Default Layout，404 catch-all 于注册后追加；`stores/permission.ts` routesLoaded 支持刷新恢复（sessionStorage token 镜像 + whoami 重取），退出/被踢经 auth-broadcast 统一 removeRoute+清态，不产生重复路由或无权限菜单泄露。注：本项虽标 P1，但为批次5 页面导航硬性前提，随批次5 完成）
- 所属工程: admin-server | admin-web
- 阶段: P1(重要)
- 前置依赖: T-0011, T-0019, T-0033
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-MENU-04、design.md §9.6、§10.3
- 任务描述: 在 `admin-server/` 提供当前用户可见菜单树查询，在 `admin-web/src/router/` 实现静态登录/403/404 路由、登录后按菜单树递归注册动态路由、重复刷新恢复和路由权限守卫。
- 验收标准: 登录成功或自动续登后只加载当前用户被授权的路由；未授权路由跳转到 403；未知路由进入 404；刷新当前动态路由时不会产生重复路由或无权限菜单泄露。

## 任务 T-0039: 实现角色保护与菜单排序显隐增强
- 状态: [x] 已完成（批次6 admin-server：`AdminRoleService` 内置超管角色降级保护——不可停用（12003）且 assignMenus 拒绝移除 user:*/menu:* 授权（防变相降级）；`AdminMenuService.batchSort`（同父级有序ID清单校验+重写 sortOrder）与 `changeVisible`（按钮型拒绝，显隐 0/1）+ `MenuController` `PUT /menus/batch-sort`、`PUT /menus/{id}/visible`（menu:edit + 审计）；`routes()` 按 isVisible=1 收敛使显隐保存后立即反映到当前用户可见路由，sortOrder 排序即时反映列表；admin-web 菜单页：同级上移/下移按钮（步进式排序，提交 batch-sort，无第三方拖拽依赖）与可见开关列，保存后树与路由同步刷新；角色页删除保护批次5已备本轮沿用）
- 所属工程: admin-server | admin-web
- 阶段: P1(重要)
- 前置依赖: T-0019, T-0033, T-0038
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-ROLE-04、FR-MENU-03、design.md §5.3、§9.6
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/system/role/`、`admin-web/src/views/system/menu/` 实现内置角色删除/降级保护、引用校验，以及目录和菜单的拖拽排序、可见性切换。
- 验收标准: 超级管理员等内置角色不能被删除或被配置为无管理权限；已失效路由或被业务引用的内置角色不能降级；菜单排序和显隐保存后立即反映到列表与当前用户可见路由。

## 任务 T-0040: 落地通知公告 schema
- 状态: [x] 已完成（批次6，`cross-cutting/database/schema/007-sys-notice.sql`：`sys_notice`（标题/内容 TEXT/生效/失效/置顶/状态 0=草稿 1=已发布/发布人 id+名冗余/发布时间 + BaseEntity 公共列）与 `sys_notice_read`（联合唯一 notice_id+user_id 的追加式已读记录）；公告与 admin_op_log/admin_login_log 完全分表不混用（验收）；配套 `seed/002-admin-batch6.sql` 登记公告/系统设置菜单与 notice:*/sysconfig:*/user:export/user:import 按钮权限及角色授权增量，幂等可重复执行）
- 所属工程: cross-cutting
- 阶段: P2(可选)
- 前置依赖: T-0001
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SYS-02~FR-SYS-03、requirements.md §5.1、design.md §10.3
- 任务描述: 在 `cross-cutting/database/` 规划并落地公告、公告有效时间、置顶、发布人与用户已读状态的数据结构，供后续公告 CRUD 和登录后通栏展示使用。
- 验收标准: 公告可表达标题、内容、生效、失效、置顶与发布人；用户能区分已读和未读公告；公告不与操作审计或登录日志混用。

## 任务 T-0041: 实现系统设置管理与读取页面
- 状态: [x] 已完成（批次6 admin-server：`sys/service/SysConfigAdminService.java`（受控列表；批量更新仅允许已登记稳定键 11008、按 value_type 强校验 11009、不开放增删键；更新成功即 `SysConfigReader.refresh()` 使 60s 缓存立即失效，登录/限流/认证链路即时读到新值）+ `sys/controller/SysConfigController.java` `GET /sys/configs`（sysconfig:view，审计员只读）、`PUT /sys/configs/batch`（sysconfig:edit + @OperationLog module=sysconfig）；敏感内部凭证/ApiKey 不在 sys_config 登记范围，天然无法经此配置；admin-web：`api/sys.ts` + `views/system/config/index.vue`（与 seed/002 菜单 component=system/config/index 对齐）BOOLEAN 键开关控件/NUMBER 键输入框、未保存标记、只读模式（无 sysconfig:edit 时禁用编辑），新注册/登录阈值/锁定时长/最长免登天数/默认限流/匿名灰度六类设置项全覆盖）
- 所属工程: admin-server | admin-web
- 阶段: P2(可选)
- 前置依赖: T-0006, T-0010, T-0011, T-0016
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SYS-01、design.md §4.5、§7.6
- 任务描述: 在 `admin-server/` 实现管理端简单 KV 的受控列表与批量更新，在 `admin-web/src/views/system/config/` 提供新注册开关、登录阈值、锁定时长、最长免登天数、默认限流值和匿名调用灰度开关的管理界面。
- 验收标准: 普通用户不能修改系统设置；已配置值可供登录、限流和认证代码读取；匿名调用开关默认严格；管理端内部凭证和 ApiKey 不得通过此页面配置。

## 任务 T-0042: 实现通知公告 CRUD 与登录后投递
- 状态: [x] 已完成（批次6 admin-server：`sys/entity/SysNotice+SysNoticeRead` + 两 Mapper + `SysNoticeService`（草稿/发布/下线/编辑/逻辑删除、生效窗口校验、投递仅返回已发布且 effective<=now<expire 的公告、markRead 幂等、readIds 计算）+ `SysNoticeController`（管理端点 /sys/notices CRUD 按 notice:add/edit/delete 权限码 + @OperationLog 审计；投递端点 /notices/inbox、/notices/unread-count、/notices/{id}/read 仅要求登录）；admin-web：`views/system/notice/index.vue`（component=system/notice/index 对齐 seed/002）管理页（新增/编辑/发布/下线/删除，普通用户无 notice:add 即纯阅读形态）、`layouts/DefaultLayout.vue` 顶栏公告铃铛+未读徽标+站内信弹层（点开标记已读）+登录后未读公告通栏（60s 轮询，可关闭）；未到生效/已超失效公告不展示、普通用户不能管理且后端独立拒绝、写操作全部入审计（验收））
- 所属工程: admin-server | admin-web
- 阶段: P2(可选)
- 前置依赖: T-0040, T-0010, T-0011, T-0016, T-0021
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SYS-02~FR-SYS-03、design.md §10.3
- 任务描述: 在 `admin-server/` 实现管理员公告新增、编辑、删除、发布、生效、失效、置顶和只读用户已读状态，在 `admin-web/src/views/system/notice/`、`admin-web/src/layouts/` 与用户状态模块提供管理页、顶部通栏和站内信列表。
- 验收标准: 未到生效时间或超过失效时间的公告不展示；已读与未读状态可区分；普通用户只能读公告不能管理；公告管理写操作进入管理端审计。

## 任务 T-0043: 实现用户 CSV 导入与导出
- 状态: [x] 已完成（批次6 admin-server：`common/util/ExportUtil.java`（零依赖 CSV/SpreadsheetML 工具）+ `AdminUserService.exportUsers`（列：用户名/昵称/邮箱/手机/部门/状态/角色/最后登录/创建时间，范围=当前筛选+数据权限，不含密码/ApiKey/内部凭证——验收）与 `importUsers`（内置极简 RFC4180 CSV 解析；重复用户名/非法邮箱/未知角色/缺必填逐行拒绝并给行号+原因，不静默覆盖；统一 ≥8 位初始密码 + firstLogin=1 强制首登改密；逐行成功/失败结果 `UserImportResultVO`）+ `UserController` `GET /users/export`（user:export）、`POST /users/import`（user:import + multipart + @OperationLog）；admin-web 用户页"导出 CSV/导入 CSV"按钮（v-permission 控制）与导入对话框（文件选择+初始密码+逐行结果明细表）；权限码 user:export/user:import 落 seed/002）
- 所属工程: admin-server | admin-web
- 阶段: P2(可选)
- 前置依赖: T-0018
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-USER-07、design.md §10.3
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/system/user/` 实现用户基础信息 CSV 批量导入、导出、字段校验、结果反馈和权限控制。
- 验收标准: 无用户管理权限不能导入或导出；重复用户名、非法字段和缺失必填项不静默覆盖；导入结果明确表达成功与失败记录；导出范围遵守当前用户数据权限，密码、ApiKey 和内部凭证不出现在导出内容中。

## 任务 T-0044: 实现无活跃会话批量清理
- 状态: [x] 已完成（批次6 admin-server：`session/dto/SessionBatchDestroyRequest.java` + `SessionAdminService.batchDestroy`（inactiveMinutes 自动筛选口径强制排除默认会话——不被隐式纳入，验收；sessionIds 显式清单仅取可见域内会话，不可见项计入失败回执不静默丢弃）逐项经 Bridge 调 DELETE /internal/sandbox/sessions/{id} 聚合逐项结果 + `countInactiveTargets` 预览 + `GET /sessions/batch/preview`、`POST /sessions/batch-destroy`（均 session:force，仅超管/管理员；@OperationLog 整体与逐项进入审计）；python-sandbox 内部单会话强销接口复用 T-0026 契约（批量在 admin-server 侧逐项编排，未改内部契约）；admin-web 会话页"批量清理"对话框（阈值预览目标数量确认 + 勾选显式目标含默认会话高危确认 + 逐项成功/失败/剩余会话结果表，任一失败不虚构全部成功））
- 所属工程: admin-server | admin-web | python-sandbox
- 阶段: P2(可选)
- 前置依赖: T-0026, T-0031, T-0037
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SESSION-06、design.md §8.4、§10.3
- 任务描述: 在 `admin-server/` 内部会话封装中增加按最后活跃时间筛选并批量强销的受控能力，在 `admin-web/src/views/session/` 提供管理员批量选择、目标数量确认、逐项结果和默认会话保护。
- 验收标准: 仅管理员可执行；目标集合必须先按 N 分钟阈值筛选并在界面确认；任一失败不虚构全部成功，默认会话不能被隐式纳入；每次批量动作及逐项结果进入管理端审计。

## 任务 T-0045: 实现调用记录 CSV 与 Excel 导出
- 状态: [x] 已完成（批次6 admin-server：`LogQueryService` 抽出 `buildApiWrapper/buildSandboxWrapper` 复用筛选与排序口径，新增 `listApiLogForExport/listSandboxLogForExport`（与分页同筛选+排序+SELF 数据权限，上限 10000 条受控）+ `common/util/ExportUtil.java`（零依赖 CSV（UTF-8 BOM，Excel 双击不乱码）与 SpreadsheetML Excel XML）+ `LogQueryController` `GET /logs/api/export`、`GET /logs/sandbox/export`（format=csv|excel，@SaCheckPermission("apilog:export") + @OperationLog module=apilog type=export 审计，Content-Disposition 中文名 RFC5987 编码）；导出列保留各截断布尔派生的"已截断/完整"标记列（不无提示丢失截断状态，FR-LOG-04 一致），永不含 ApiKey 明文/密码/验证码/Remember-Me token/内部凭证（apikey 列仅名称+前缀+掩码标签）；admin-web 日志两页签查询区各挂"导出CSV/导出Excel"按钮（v-permission apilog:export，apilog:export 权限码 seed 已存在）经 responseType=blob 触发下载）
- 所属工程: admin-server | admin-web
- 阶段: P2(可选)
- 前置依赖: T-0032
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-LOG-06、design.md §10.3
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/log/` 增加按当前筛选结果导出 CSV/Excel 的受控能力，并在导出内容中保留截断状态与数据可见域口径。
- 验收标准: 导出结果与当前筛选、排序和权限范围一致；API 日志与沙箱操作日志可按页面能力导出；长内容不无提示地丢失截断标记；ApiKey 明文、密码、验证码、Remember-Me token 和内部凭证不出现在导出内容中。
