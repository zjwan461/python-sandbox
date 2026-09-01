# 任务清单 - Python Sandbox 管理端

## 工程目录布局示意

- 仓库一级目录采用并列布局：`admin-web/`、`admin-server/`、`cross-cutting/`、`python-sandbox/`、`sdk/`、`train/`、`docs-site/` 等，彼此不存在源码包含、构建嵌入或模块依赖关系。
- `admin-web/` 为独立的 Vue 3 + TypeScript + Vite 工程，`admin-server/` 为独立的管理端后端工程；两者全部产物、源码和配置均只落在各自一级目录内。
- `python-sandbox/` 仅在既有工程结构内完成 ApiKey、限流、活跃会话、内部接口和日志字段改造。
- `cross-cutting/` 仅承载三工程共享的 schema 增量、ER 对齐资料与初始化数据；`admin-server/` 不得 import、复制或直接调用 `python-sandbox/` 下的类、常量或工具。
- 管理端与 `python-sandbox/` 共享同一 MySQL 库时，只能经由 HTTP 或数据库访问既定数据边界；`admin-server/` 不直接连接 Docker，不修改 `python-sandbox/` 执行业务。
- 后续所有新增、修改任务的合法目标路径仅为 `admin-web/**`、`admin-server/**`、`python-sandbox/**` 或 `cross-cutting/**`；跨目录源码依赖与越界工程改造不纳入本清单。

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
- 状态: [-] admin-server 部分已完成（批次2，`auth/controller/AuthController.java` + `auth/service/AuthService.java`：账密+验证码+失败锁定读 sys_config+admin_login_log 落库+Sa-Token 签发+首登强制改密标记透出+注销+被踢下线 20004 语义；admin-web 部分依赖 T-0011，未在本批次范围）
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
- 状态: [-] admin-server 部分已完成（批次2：`PUT /auth/password` 校验旧密码、成功后 `StpUtil.logout` 作废旧会话、first_login 解除标记；管理员重置密码/手动解锁/启停用见 `/users/{id}/reset-password`、`/users/{id}/unlock`、`/users/{id}/status`；admin-web 部分未在本批次范围）
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0015, T-0016
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-AUTH-05~FR-AUTH-07、design.md §11.1
- 任务描述: 在 `admin-server/` 与 `admin-web/` 实现登录用户自身密码修改、修改后旧令牌作废、首次登录强制改密状态以及管理员重置密码和手动解锁所需的业务联动。
- 验收标准: 修改密码后所有未失效旧会话立即作废；首次登录用户不能跳过改密流程；管理员可重置任意账号密码并能按 FR-AUTH-05 语义手动解锁；前端提供改密、重置和锁定状态表达，不展示密码或密码摘要。

## 任务 T-0018: 实现用户管理后端与前端
- 状态: [-] admin-server 部分已完成（批次2，`rbac/controller/UserController.java` + `rbac/service/AdminUserService.java`：分页筛选、详情、新增/编辑、启停用（停用即时踢下线+最后超管保护+禁自停用）、重置密码、手动解锁、分配角色（变更作废旧会话）、用户名唯一、至少一角色校验、VO 不含密码字段；用户软删除与历史归属转移、删除阻断校验依赖批次3 的 ApiKey/会话数据，本批次未实现；admin-web 部分未在本批次范围）
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0002, T-0009, T-0010, T-0011, T-0015, T-0016, T-0017
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-USER-01~FR-USER-06、FR-USER-08、FR-RBAC-04、requirements.md §10.3.8、design.md §5.5、§10.3
- 任务描述: 在 `admin-server/src/main/java/` 与 `admin-web/src/views/system/user/`、`admin-web/src/api/` 实现用户分页列表、多条件筛选、字段排序、新增、编辑、单删与批量删除、启停、角色绑定、重置密码、锁定标记及个人中心；用户部门使用可空文本，并落实普通用户软删除后的历史数据归属转移。
- 验收标准: FR-USER-01~FR-USER-06 与 FR-USER-08 的字段和动作完整可用；已登录或仍持有效 ApiKey 的用户删除按业务规则阻止；可删除的普通用户停用或软删除后，其历史数据仅管理员和审计员可见且不存在悬空归属；停用用户及其 ApiKey 在 `python-sandbox/` 侧形成明确拒绝语义；普通用户不能访问他人用户管理数据或管理动作，越权请求不返回目标数据。

## 任务 T-0019: 实现角色与菜单管理后端和前端
- 状态: [-] admin-server 部分已完成（批次2，`rbac/controller/RoleController.java`+`AdminRoleService.java`：列表/增改删/启停用/分配菜单，roleKey 唯一、内置角色不可删不可改权限字符、被引用不可删、授权变更后作废受影响会话；`rbac/controller/MenuController.java`+`AdminMenuService.java`：树形 CRUD、M/C/F 三类校验（按钮必填 perms）、子节点删除阻断、`GET /menus/routes` 按角色过滤的动态菜单树；admin-web 部分未在本批次范围）
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0002, T-0009, T-0010, T-0011, T-0016
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-ROLE-01~FR-ROLE-03、FR-MENU-01~FR-MENU-02、design.md §5.2、§5.3、§10.3
- 任务描述: 在 `admin-server/src/main/java/` 与 `admin-web/src/views/system/role/`、`admin-web/src/views/system/menu/` 实现角色列表、增改删、状态、菜单和按钮权限分配，以及菜单树增改删、按钮权限标识维护和当前用户可访问菜单路由数据。
- 验收标准: 角色权限字符唯一；用户至少可关联一个角色；菜单树可表达目录、菜单、按钮、路由、组件、外链、缓存和显隐；按钮权限采用统一权限字符；当前用户只能得到自身角色授权的菜单与按钮数据。

## 任务 T-0020: 实现管理端审计日志落库与查询
- 状态: [-] admin-server 部分已完成（批次2，`audit/entity/AdminLoginLog|AdminOpLog`（与 schema/004 字段一致，只追加）、登录日志随登录流程落库（SUCCESS/FAIL/LOCKED+原因+IP/UA）、`audit/annotation/OperationLog`+`audit/aspect/OpLogAspect` 写操作切面（操作人/模块/类型/目标/traceId/IP/UA）、`audit/controller/AuditLogController` 只读分页查询（loginlog:view/oplog:view 权限码，普通用户无授权即越权拒绝）；用户/角色/菜单写操作已挂注解。审计查询前端页面（admin-web/src/views/audit/）未在本批次范围）
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
- 所属工程: python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0003
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-LOG-01~FR-LOG-03、FR-RBAC-03、requirements.md §5.2、design.md §8.5、§8.6、§12
- 任务描述: 仅在 `python-sandbox/src/main/java/io/github/sandbox/entity/`、`python-sandbox/src/main/resources/` 与既有 Mapper/Aspect 边界内，为 `api_log` 增加客户端、ApiKey、归属用户、限流命中与命中规则字段，为 `sandbox_operation_log` 增加相同归属字段，并修改 `ApiLogAspect`、`SandboxOperationLogAspect` 从请求与限流上下文填充这些新增字段。
- 验收标准: 既有列和既有写入语义不破坏；新字段在无鉴权上下文时允许为空；ApiLog 的限流命中与规则标识可被查询；操作日志可通过相同归属字段关联 ApiKey 和客户端；不复制 `admin-server/` 的实体或配置。

## 任务 T-0023: 实现 python-sandbox ApiKey 校验过滤器与上下文传递
- 所属工程: python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0003, T-0022
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-CLIENT-03~FR-CLIENT-04、FR-APIKEY-05、FR-LOG-01、requirements.md §6.1、design.md §8.1、§8.2
- 任务描述: 仅在 `python-sandbox/src/main/java/io/github/sandbox/filter/` 与既有 Web 配置边界内实现所有 `/api/sandbox/**` 请求的 ApiKey 校验，区分缺失、未知、停用、撤销、过期、未生效、客户端停用和归属用户停用语义，并向后续限流与日志组件传递客户端、ApiKey 和归属用户上下文。
- 验收标准: 匿名灰度关闭时缺失或无效 ApiKey 明确拒绝；灰度开启时允许匿名调用但仍应用全局默认限流；校验通过后 traceId、ApiKey、客户端、用户上下文可供现有 Aspect 使用；用户、客户端、ApiKey 任一停用均拒绝对应调用；内部 `/internal/**` 不进入该 ApiKey 鉴权通道。

## 任务 T-0024: 实现 python-sandbox 限流拉取组件与本地判定
- 所属工程: python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0004, T-0023
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-RATELIMIT-01~FR-RATELIMIT-04、requirements.md §6.2、design.md §7.2~§7.5、§8.1、§8.3
- 任务描述: 仅在 `python-sandbox/src/main/java/io/github/sandbox/` 与 `python-sandbox/src/main/resources/` 实现启动加载和定时从 MySQL 拉取限流规则到本地缓存，按 ApiKey 滑动窗口、客户端令牌桶及全局默认规则执行判定并处理白名单。
- 验收标准: 规则在启动后进入本地缓存并可按配置周期刷新；同一目标多规则命中任一即拒绝；白名单 ApiKey 跳过全部规则；ApiKey 限流命中写入扩展 ApiLog 字段并通过明确限流响应表达；拉取失败保留旧缓存并使当前调用获得“配置未最新”的明确业务语义；不依赖 `admin-server/` 的 Java 类或内部网关。

## 任务 T-0025: 实现 python-sandbox 活跃会话接口
- 所属工程: python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0003, T-0023
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SESSION-01、requirements.md §6.3、design.md §8.4、§10.4
- 任务描述: 仅在 `python-sandbox/src/main/java/io/github/sandbox/controller/`、`python-sandbox/src/main/java/io/github/sandbox/service/` 增加从内存会话快照生成活跃会话列表和详情关联数据的能力，保留 sessionId、containerId、容器名、创建时间、最后活跃时间、默认标记和客户端/ApiKey/用户归属。
- 验收标准: 返回内容只来自当前进程内活跃会话；会话所属信息与 ApiKey 调用上下文一致；服务重启后无法枚举的孤儿会话不被伪造；既有强销能力保持可用且不改变 `/api/sandbox/**` 执行接口契约。

## 任务 T-0026: 实现 python-sandbox 内部接口与内部凭证校验
- 所属工程: python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0010, T-0024, T-0025
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): requirements.md §6.3、§6.5、design.md §6.3、§10.4、§13
- 任务描述: 仅在 `python-sandbox/src/main/java/` 与 `python-sandbox/src/main/resources/application.yml` 增加 `/internal/**` 边界、独立 `X-Admin-Internal-Token` 校验、活跃会话列表/详情/强销和按需刷新限流规则的内部能力；凭证支持环境变量覆盖且不写入数据库。
- 验收标准: 内部凭证与客户端 ApiKey 完全分离；缺失或错误内部凭证返回明确未授权语义；只有管理端内部链路可调用会话与限流刷新能力；内部接口不暴露沙箱执行细节；强销回执表达成功/失败及剩余会话数。

## 任务 T-0027: 实现 admin-server 对接 python-sandbox 的内部调用封装
- 所属工程: admin-server
- 阶段: P0(必须)
- 前置依赖: T-0010, T-0012, T-0026
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): requirements.md §5.3、§6.3、§6.5、design.md §1.3、§6.3、§10.4
- 任务描述: 在 `admin-server/src/main/java/io/github/sandbox/admin/bridge/` 建立统一 WebClient 或 RestTemplate 客户端，封装 `/internal/**` 调用、从 `admin-server/src/main/resources/application.yml` 读取可由 ENV 覆盖的 `X-Admin-Internal-Token` 并统一注入请求、处理内部错误和返回会话与限流刷新语义。
- 验收标准: 管理端业务 Controller 不直接硬编码 `python-sandbox/` URL 或凭证；每次内部调用只使用管理端配置中的同一内部凭证；客户端 ApiKey 不会发送到该网关；内部调用失败转换为管理端明确业务错误；该模块不导入 `python-sandbox/` 代码。

## 任务 T-0028: 实现客户端管理后端与前端
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0003, T-0009, T-0010, T-0011, T-0016, T-0020, T-0021
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-CLIENT-01~FR-CLIENT-04、FR-RBAC-02~FR-RBAC-04、design.md §6.1、§10.3
- 任务描述: 在 `admin-server/` 实现客户端分页列表、名称/编码/归属人/状态筛选、新增编辑、启停、删除前 ApiKey 处理和归属权限，在 `admin-web/src/views/client/` 实现对应页面、表单和状态操作。
- 验收标准: 客户端编码唯一；普通用户只能管理自己的客户端；停用客户端后其名下启用 ApiKey 被 `python-sandbox/` 拒绝；删除仍持有有效 ApiKey 的客户端前必须给出处理提示且完成阻断；管理员和审计员的查看范围符合 FR-RBAC-01。

## 任务 T-0029: 实现 ApiKey 管理后端与前端
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0003, T-0009, T-0010, T-0011, T-0016, T-0020, T-0021, T-0023, T-0028
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-APIKEY-01~FR-APIKEY-08、FR-RBAC-02~FR-RBAC-04、design.md §6.2、§11.2
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/apikey/` 实现 ApiKey 创建、客户端与可选用户绑定、生效/过期时间、状态、撤销、重新生成、列表筛选、一次性明文展示、复制与重新生成入口。
- 验收标准: 创建响应只允许携带一次明文；一次性展示期内可复制且后续列表和详情只显示前缀与后四位掩码；重新生成会替换旧凭证并撤销旧 ApiKey；撤销不可逆；未生效、过期、停用和撤销状态可筛选并由 `python-sandbox/` 拒绝；创建、启停、撤销和重新生成均进入 `admin_op_log`。

## 任务 T-0030: 实现限流规则管理后端与前端
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0004, T-0009, T-0010, T-0011, T-0016, T-0020, T-0021, T-0024, T-0028, T-0029
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-RATELIMIT-01~FR-RATELIMIT-04、FR-RATELIMIT-03、design.md §7.1、§7.3、§7.4
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/ratelimit/` 实现按 ApiKey 或客户端配置规则、分页列表、分钟/小时/天窗口、正整数阈值、优先级、状态、有效期及保存后触发 `python-sandbox/` 刷新本地规则的业务入口。
- 验收标准: 规则维度与目标类型一致；目标不存在、阈值非法或状态冲突时不能保存；启用规则在启动或定时拉取后进入 `python-sandbox/`；前端可查看规则目标、状态与优先级；规则保存与刷新动作有明确管理端审计记录。

## 任务 T-0031: 实现运行中会话列表、详情与强制销毁闭环
- 所属工程: admin-server | admin-web | python-sandbox
- 阶段: P0(必须)
- 前置依赖: T-0020, T-0021, T-0025, T-0026, T-0027, T-0028
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SESSION-01、FR-SESSION-04、FR-RBAC-01~FR-RBAC-04、requirements.md §5.3、design.md §8.4、§10.4、§11.4
- 任务描述: 在 `python-sandbox/` 沿用并增强内部强销语义，在 `admin-server/` 封装列表与强销业务接口，在 `admin-web/src/views/session/` 实现活跃会话列表、普通用户本人数据范围、管理员二次确认、默认会话强调和销毁结果回写。
- 验收标准: 列表展示 sessionId、containerId、容器名、创建/最后活跃时间、ApiKey、客户端、用户和默认标记；普通用户不显示他人会话；管理员确认后调用内部强销入口；销毁失败不从前端移除记录且保留失败结果；成功销毁后列表语义反映会话已不存在并写入操作审计。

## 任务 T-0032: 实现调用记录查询与 traceId 链路详情
- 所属工程: admin-server | admin-web
- 阶段: P0(必须)
- 前置依赖: T-0009, T-0010, T-0011, T-0016, T-0020, T-0021, T-0022, T-0023
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-LOG-01~FR-LOG-05、FR-LOG-07、FR-RBAC-01~FR-RBAC-03、design.md §5.5、§8.5、§10.3
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/log/` 实现 API 日志和沙箱操作日志的筛选分页、默认时间倒序、自定义排序、详情及按 traceId 聚合链路，输出请求参数、耗时、标准输出、标准错误、退出码、错误信息和截断标记。
- 验收标准: 筛选覆盖需求列出的时间、ApiKey、客户端、用户、方法、路径、状态码、traceId、IP、类型和结果；普通用户只获得本人可见域；管理员和审计员获得全部记录；同 traceId 的 API 日志与多次操作可同屏查看；截断内容明确标记且不伪装为完整内容。

## 任务 T-0033: 实现前端 v-permission 按钮权限指令
- 所属工程: admin-web
- 阶段: P0(必须)
- 前置依赖: T-0011, T-0019
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-MENU-02、design.md §5.3、§9.4
- 任务描述: 在 `admin-web/src/directives/` 与 `admin-web/src/stores/` 实现全局 `v-permission` 指令、权限码状态接入和任意匹配/全部匹配语义，并接入角色、菜单、ApiKey、限流、审计及会话等管理动作。
- 验收标准: 具备权限时显示对应操作控件；不匹配时控件不保留在可见 DOM；支持需求约定的任意或全部匹配；后端权限校验仍独立存在，隐藏控件不能绕过服务端授权。

## 任务 T-0034: 完成 Remember-Me 前后端闭环
- 所属工程: admin-server | admin-web
- 阶段: P1(重要)
- 前置依赖: T-0006, T-0007, T-0009, T-0010, T-0013, T-0015, T-0016
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-AUTH-03、FR-AUTH-04、design.md §4.2、§9.3、§11.1
- 任务描述: 在 `admin-server/` 实现与短期 token 分离的 Remember-Me 长 token 签发、续登、过期和 HttpOnly Cookie 通道，在 `admin-web/src/views/auth/` 接入记住我选项、自动续登、主动退出与被踢下线后的长 token 失效。
- 验收标准: 勾选记住我后浏览器通过 HttpOnly Cookie 自动携带长 token；未勾选时不签发长 token；超过“最长免登天数”或主动退出后长 token 立即失效；前端不把长期 token 写入 Pinia 或其他可被脚本读取的本地存储；客户端 ApiKey 不进入 Remember-Me 通道。

## 任务 T-0035: 实现客户端统计卡片与归属转移
- 所属工程: admin-server | admin-web
- 阶段: P1(重要)
- 前置依赖: T-0028, T-0029, T-0032
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-CLIENT-05~FR-CLIENT-06、design.md §10.3
- 任务描述: 在 `admin-server/` 聚合客户端 ApiKey 数量、活跃 ApiKey 数量、今日调用次数和累计调用次数，并提供有权限的客户端归属转移；在 `admin-web/src/views/client/` 展示统计卡片和转移操作。
- 验收标准: 统计口径与当前客户端筛选范围一致；归属转移成功后该客户端及历史调用记录按新归属用户展示；普通用户不能转移他人客户端；越权转移被拒绝并进入管理端审计。

## 任务 T-0036: 实现多规则叠加、白名单与全局默认限流
- 所属工程: admin-server | admin-web | python-sandbox
- 阶段: P1(重要)
- 前置依赖: T-0024, T-0029, T-0030
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-RATELIMIT-02、FR-RATELIMIT-05~FR-RATELIMIT-06、design.md §7.3、§7.4、§7.6
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/ratelimit/` 增加 ApiKey 无限流标志、多个规则叠加和全局默认规则配置，在 `python-sandbox/` 按 ApiKey、客户端、全局默认优先级完成对应判定。
- 验收标准: 同一目标可叠加多条规则且任一命中即拒绝；白名单 ApiKey 跳过全部限流；未匹配任何专属规则的调用方受全局默认规则约束；普通用户不能修改不属于自己的目标规则；相关配置和命中结果符合既有审计与查询要求。

## 任务 T-0037: 实现会话筛选、关联日志详情与默认会话保护
- 所属工程: admin-server | admin-web | python-sandbox
- 阶段: P1(重要)
- 前置依赖: T-0025, T-0031, T-0032
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SESSION-02~FR-SESSION-03、FR-SESSION-05、design.md §8.4、§10.3、§10.4
- 任务描述: 在 `admin-server/`、现有 `python-sandbox/` 内部会话数据边界和 `admin-web/src/views/session/` 增加 ApiKey、客户端、用户、活跃时间与默认会话筛选，关联最近 API 日志和沙箱操作日志，并强化默认会话销毁确认语义。
- 验收标准: 每种筛选只返回匹配活跃会话；详情可查看该会话关联日志并遵守数据权限；默认会话销毁必须在管理员显式二次确认后执行；其他用户无强销入口。

## 任务 T-0038: 实现基于后端菜单的动态路由加载
- 所属工程: admin-server | admin-web
- 阶段: P1(重要)
- 前置依赖: T-0011, T-0019, T-0033
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-MENU-04、design.md §9.6、§10.3
- 任务描述: 在 `admin-server/` 提供当前用户可见菜单树查询，在 `admin-web/src/router/` 实现静态登录/403/404 路由、登录后按菜单树递归注册动态路由、重复刷新恢复和路由权限守卫。
- 验收标准: 登录成功或自动续登后只加载当前用户被授权的路由；未授权路由跳转到 403；未知路由进入 404；刷新当前动态路由时不会产生重复路由或无权限菜单泄露。

## 任务 T-0039: 实现角色保护与菜单排序显隐增强
- 所属工程: admin-server | admin-web
- 阶段: P1(重要)
- 前置依赖: T-0019, T-0033, T-0038
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-ROLE-04、FR-MENU-03、design.md §5.3、§9.6
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/system/role/`、`admin-web/src/views/system/menu/` 实现内置角色删除/降级保护、引用校验，以及目录和菜单的拖拽排序、可见性切换。
- 验收标准: 超级管理员等内置角色不能被删除或被配置为无管理权限；已失效路由或被业务引用的内置角色不能降级；菜单排序和显隐保存后立即反映到列表与当前用户可见路由。

## 任务 T-0040: 落地通知公告 schema
- 所属工程: cross-cutting
- 阶段: P2(可选)
- 前置依赖: T-0001
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SYS-02~FR-SYS-03、requirements.md §5.1、design.md §10.3
- 任务描述: 在 `cross-cutting/database/` 规划并落地公告、公告有效时间、置顶、发布人与用户已读状态的数据结构，供后续公告 CRUD 和登录后通栏展示使用。
- 验收标准: 公告可表达标题、内容、生效、失效、置顶与发布人；用户能区分已读和未读公告；公告不与操作审计或登录日志混用。

## 任务 T-0041: 实现系统设置管理与读取页面
- 所属工程: admin-server | admin-web
- 阶段: P2(可选)
- 前置依赖: T-0006, T-0010, T-0011, T-0016
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SYS-01、design.md §4.5、§7.6
- 任务描述: 在 `admin-server/` 实现管理端简单 KV 的受控列表与批量更新，在 `admin-web/src/views/system/config/` 提供新注册开关、登录阈值、锁定时长、最长免登天数、默认限流值和匿名调用灰度开关的管理界面。
- 验收标准: 普通用户不能修改系统设置；已配置值可供登录、限流和认证代码读取；匿名调用开关默认严格；管理端内部凭证和 ApiKey 不得通过此页面配置。

## 任务 T-0042: 实现通知公告 CRUD 与登录后投递
- 所属工程: admin-server | admin-web
- 阶段: P2(可选)
- 前置依赖: T-0040, T-0010, T-0011, T-0016, T-0021
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SYS-02~FR-SYS-03、design.md §10.3
- 任务描述: 在 `admin-server/` 实现管理员公告新增、编辑、删除、发布、生效、失效、置顶和只读用户已读状态，在 `admin-web/src/views/system/notice/`、`admin-web/src/layouts/` 与用户状态模块提供管理页、顶部通栏和站内信列表。
- 验收标准: 未到生效时间或超过失效时间的公告不展示；已读与未读状态可区分；普通用户只能读公告不能管理；公告管理写操作进入管理端审计。

## 任务 T-0043: 实现用户 CSV 导入与导出
- 所属工程: admin-server | admin-web
- 阶段: P2(可选)
- 前置依赖: T-0018
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-USER-07、design.md §10.3
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/system/user/` 实现用户基础信息 CSV 批量导入、导出、字段校验、结果反馈和权限控制。
- 验收标准: 无用户管理权限不能导入或导出；重复用户名、非法字段和缺失必填项不静默覆盖；导入结果明确表达成功与失败记录；导出范围遵守当前用户数据权限，密码、ApiKey 和内部凭证不出现在导出内容中。

## 任务 T-0044: 实现无活跃会话批量清理
- 所属工程: admin-server | admin-web | python-sandbox
- 阶段: P2(可选)
- 前置依赖: T-0026, T-0031, T-0037
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-SESSION-06、design.md §8.4、§10.3
- 任务描述: 在 `admin-server/` 内部会话封装中增加按最后活跃时间筛选并批量强销的受控能力，在 `admin-web/src/views/session/` 提供管理员批量选择、目标数量确认、逐项结果和默认会话保护。
- 验收标准: 仅管理员可执行；目标集合必须先按 N 分钟阈值筛选并在界面确认；任一失败不虚构全部成功，默认会话不能被隐式纳入；每次批量动作及逐项结果进入管理端审计。

## 任务 T-0045: 实现调用记录 CSV 与 Excel 导出
- 所属工程: admin-server | admin-web
- 阶段: P2(可选)
- 前置依赖: T-0032
- 涉及能力点(引用 requirements.md 的 FR-xxxx 或 design.md 章节编号): FR-LOG-06、design.md §10.3
- 任务描述: 在 `admin-server/` 与 `admin-web/src/views/log/` 增加按当前筛选结果导出 CSV/Excel 的受控能力，并在导出内容中保留截断状态与数据可见域口径。
- 验收标准: 导出结果与当前筛选、排序和权限范围一致；API 日志与沙箱操作日志可按页面能力导出；长内容不无提示地丢失截断标记；ApiKey 明文、密码、验证码、Remember-Me token 和内部凭证不出现在导出内容中。
