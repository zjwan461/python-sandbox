-- =====================================================================
-- 001-admin-seed.sql — 管理端初始化种子数据（T-0007）
-- 依据：requirements.md §4.2~§4.7；design.md §5.2、§6.2、§13（默认决策 #11）
-- 前置：已按顺序执行 python-sandbox/src/main/resources/db/init.sql 与
--       cross-cutting/database/schema/001~006 全部脚本。
-- 特性：幂等可重复执行（显式主键 + INSERT IGNORE），create_by/update_by=system。
-- 安全声明：
--   * 超管初始密码为 Admin@123，库中仅存 BCrypt(cost=10) 哈希：
--       $2b$10$Y2UJfaAxKkp.Al1DkvchTuQ0V9AmKLU8xsopCUmj6JhH9UI1pAf0W
--     首次登录强制改密（first_login=1）。
--   * 示例 ApiKey 仅存 SHA-256 摘要/前缀/掩码，对应明文为不可用演示串
--     （状态=4 已撤销 + 一次性明文已消费），任何环境中不可用于真实调用，
--     本文件与执行日志中不出现可调用明文。
-- =====================================================================

USE sandbox;

-- ---------------------------------------------------------------------
-- 1. 超级管理员账号（admin / Admin@123，BCrypt 哈希如上）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO admin_user
    (id, username, nickname, email, password_hash, status, dept_name,
     first_login, remark, create_by, update_by)
VALUES
    (1, 'admin', '超级管理员', 'admin@example.com',
     '$2b$10$Y2UJfaAxKkp.Al1DkvchTuQ0V9AmKLU8xsopCUmj6JhH9UI1pAf0W',
     1, '总部', 1, '内置超级管理员，初始密码 Admin@123（BCrypt），首次登录必须修改',
     'system', 'system');

-- ---------------------------------------------------------------------
-- 2. 默认角色（内置不可删）：超级管理员 / 管理员 / 普通用户 / 审计员
--    （任务口径要求至少含超管、普通用户、审计员；管理员为常规运维角色补充）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO admin_role
    (id, role_name, role_key, sort_order, status, built_in, remark, create_by, update_by)
VALUES
    (1, '超级管理员', 'superadmin', 1, 1, 1, '拥有全部菜单与按钮权限，内置不可删除', 'system', 'system'),
    (2, '管理员',     'admin',      2, 1, 1, '日常运维管理，含用户/客户端/ApiKey/限流/会话/日志管理', 'system', 'system'),
    (3, '普通用户',   'common',     3, 1, 1, '仅管理自身归属的客户端/ApiKey，仅查看本人调用记录', 'system', 'system'),
    (4, '审计员',     'auditor',    4, 1, 1, '只读：全部业务数据与审计日志查看，无写操作权限（默认决策#5）', 'system', 'system');

-- ---------------------------------------------------------------------
-- 3. 超管绑定超级管理员角色
-- ---------------------------------------------------------------------
INSERT IGNORE INTO admin_user_role (user_id, role_id, create_by) VALUES (1, 1, 'system');

-- ---------------------------------------------------------------------
-- 4. 默认菜单树（M=目录 C=菜单 F=按钮）
-- ---------------------------------------------------------------------
-- 目录
INSERT IGNORE INTO admin_menu (id, parent_id, menu_type, menu_name, icon, sort_order, route_path, component, perms, create_by, update_by) VALUES
    (1,  0, 'M', '系统管理', 'Setting',   90, '/system',   null, null, 'system', 'system'),
    (20, 0, 'M', '业务管理', 'Operation', 10, '/business', null, null, 'system', 'system'),
    (50, 0, 'M', '日志审计', 'Document',  80, '/audit',    null, null, 'system', 'system');

-- 菜单
INSERT IGNORE INTO admin_menu (id, parent_id, menu_type, menu_name, icon, sort_order, route_path, route_name, component, perms, create_by, update_by) VALUES
    (2,  1,  'C', '用户管理',   'User',         10, '/system/user',   'SystemUser',   'system/user/index',   'user:view', 'system', 'system'),
    (3,  1,  'C', '角色管理',   'UserFilled',   20, '/system/role',   'SystemRole',   'system/role/index',   'role:view', 'system', 'system'),
    (4,  1,  'C', '菜单管理',   'Menu',         30, '/system/menu',   'SystemMenu',   'system/menu/index',   'menu:view', 'system', 'system'),
    (21, 20, 'C', '客户端管理', 'OfficeBuilding',10,'/business/client','BusinessClient','client/index',      'client:view', 'system', 'system'),
    (22, 20, 'C', 'ApiKey管理', 'Key',          20, '/business/apikey','BusinessApikey','apikey/index',      'apikey:view', 'system', 'system'),
    (23, 20, 'C', '限流规则',   'Stopwatch',    30, '/business/ratelimit','BusinessRatelimit','ratelimit/index','ratelimit:view', 'system', 'system'),
    (24, 20, 'C', '运行中会话', 'Monitor',      40, '/business/session','BusinessSession','session/index',   'session:view', 'system', 'system'),
    (25, 20, 'C', '调用记录',   'Tickets',      50, '/business/apilog','BusinessApiLog','log/api/index',     'apilog:view', 'system', 'system'),
    (51, 50, 'C', '登录日志',   'Finished',     10, '/audit/login',   'AuditLogin',   'audit/login/index',   'loginlog:view', 'system', 'system'),
    (52, 50, 'C', '操作日志',   'List',         20, '/audit/operation','AuditOperation','audit/operation/index','oplog:view',  'system', 'system');

-- 按钮权限（动词收敛：view/add/edit/delete/disable/reset/revoke/force）
INSERT IGNORE INTO admin_menu (id, parent_id, menu_type, menu_name, sort_order, perms, create_by, update_by) VALUES
    (101, 2,  'F', '用户新增', 1, 'user:add',      'system', 'system'),
    (102, 2,  'F', '用户编辑', 2, 'user:edit',     'system', 'system'),
    (103, 2,  'F', '用户删除', 3, 'user:delete',   'system', 'system'),
    (104, 2,  'F', '用户启停', 4, 'user:disable',  'system', 'system'),
    (105, 2,  'F', '重置密码', 5, 'user:reset',    'system', 'system'),
    (111, 3,  'F', '角色新增', 1, 'role:add',      'system', 'system'),
    (112, 3,  'F', '角色编辑', 2, 'role:edit',     'system', 'system'),
    (113, 3,  'F', '角色删除', 3, 'role:delete',   'system', 'system'),
    (114, 3,  'F', '分配菜单', 4, 'role:edit',     'system', 'system'),
    (121, 4,  'F', '菜单新增', 1, 'menu:add',      'system', 'system'),
    (122, 4,  'F', '菜单编辑', 2, 'menu:edit',     'system', 'system'),
    (123, 4,  'F', '菜单删除', 3, 'menu:delete',   'system', 'system'),
    (201, 21, 'F', '客户端新增', 1, 'client:add',     'system', 'system'),
    (202, 21, 'F', '客户端编辑', 2, 'client:edit',    'system', 'system'),
    (203, 21, 'F', '客户端删除', 3, 'client:delete',  'system', 'system'),
    (204, 21, 'F', '客户端启停', 4, 'client:disable', 'system', 'system'),
    (211, 22, 'F', 'ApiKey创建', 1, 'apikey:add',       'system', 'system'),
    (212, 22, 'F', 'ApiKey编辑', 2, 'apikey:edit',      'system', 'system'),
    (213, 22, 'F', 'ApiKey启停', 3, 'apikey:disable',   'system', 'system'),
    (214, 22, 'F', 'ApiKey撤销', 4, 'apikey:revoke',    'system', 'system'),
    (215, 22, 'F', 'ApiKey重新生成', 5, 'apikey:reset', 'system', 'system'),
    (221, 23, 'F', '规则新增', 1, 'ratelimit:add',      'system', 'system'),
    (222, 23, 'F', '规则编辑', 2, 'ratelimit:edit',     'system', 'system'),
    (223, 23, 'F', '规则删除', 3, 'ratelimit:delete',   'system', 'system'),
    (224, 23, 'F', '规则启停', 4, 'ratelimit:disable',  'system', 'system'),
    (231, 24, 'F', '会话强销', 1, 'session:force',      'system', 'system'),
    (241, 25, 'F', '日志导出', 1, 'apilog:export',      'system', 'system');

-- ---------------------------------------------------------------------
-- 5. 角色-菜单授权
-- ---------------------------------------------------------------------
-- 5.1 超级管理员：全部菜单与按钮
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, create_by)
SELECT 1, id, 'system' FROM admin_menu WHERE deleted = 0;

-- 5.2 管理员：全部业务+审计+系统管理（不含菜单管理写操作可后续按需收紧；本轮与超管同集）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, create_by)
SELECT 2, id, 'system' FROM admin_menu WHERE deleted = 0;

-- 5.3 普通用户：业务目录及客户端/ApiKey/会话/调用记录（仅 view/add/edit/disable/revoke/reset，不含删除与限流管理与强销），不含系统管理与日志审计
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, create_by) VALUES
    (3, 20, 'system'),                                     -- 业务管理目录
    (3, 21, 'system'), (3, 201, 'system'), (3, 202, 'system'), (3, 204, 'system'),  -- 客户端：增改启停
    (3, 22, 'system'), (3, 211, 'system'), (3, 213, 'system'), (3, 214, 'system'), (3, 215, 'system'), -- ApiKey：创建/启停/撤销/重生
    (3, 24, 'system'),                                     -- 运行中会话（仅查看本人）
    (3, 25, 'system');                                     -- 调用记录（仅查看本人）

-- 5.4 审计员：日志审计目录 + 全部业务/审计菜单 view（无任何按钮写权限）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, create_by) VALUES
    (4, 50, 'system'), (4, 51, 'system'), (4, 52, 'system'),
    (4, 20, 'system'), (4, 21, 'system'), (4, 22, 'system'), (4, 23, 'system'), (4, 24, 'system'), (4, 25, 'system');

-- ---------------------------------------------------------------------
-- 6. 系统设置默认值（T-0006 预置键；类型受控；敏感凭证不在此表）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_config
    (id, config_key, config_value, value_type, config_name, description, is_built_in, create_by, update_by)
VALUES
    (1, 'register.allowed',            'false',  'BOOLEAN', '是否允许新注册',       '关闭时管理端不提供自助注册入口', 1, 'system', 'system'),
    (2, 'login.fail.threshold',        '5',      'NUMBER',  '登录失败锁定阈值',     '连续失败达到该次数即锁定账号（requirements.md 默认值 5）', 1, 'system', 'system'),
    (3, 'login.lock.minutes',          '30',     'NUMBER',  '登录锁定时长（分钟）', '账号被锁定后不可登录的时长（默认 30 分钟）', 1, 'system', 'system'),
    (4, 'remember.me.max.days',        '14',     'NUMBER',  '最长免登天数',         '"记住我"长期 token 的最长有效期（天）', 1, 'system', 'system'),
    (5, 'ratelimit.default.minute',    '60',     'NUMBER',  '全局默认限流-每分钟',  '未匹配任何专属规则调用方的每分钟默认阈值（FR-RATELIMIT-06）', 1, 'system', 'system'),
    (6, 'ratelimit.default.hour',      '1000',   'NUMBER',  '全局默认限流-每小时',  '未匹配任何专属规则调用方的每小时默认阈值', 1, 'system', 'system'),
    (7, 'ratelimit.default.day',       '10000',  'NUMBER',  '全局默认限流-每天',    '未匹配任何专属规则调用方的每天默认阈值', 1, 'system', 'system'),
    (8, 'ratelimit.anonymous.allowed', 'false',  'BOOLEAN', '匿名调用灰度开关',     'true 时允许缺失 ApiKey 的调用通过校验并按全局默认限流约束（默认决策 #10：默认严格 false）', 1, 'system', 'system');

-- ---------------------------------------------------------------------
-- 7. 示例客户端与示例 ApiKey 元数据（仅用于列表识别，不可用）
--    示例 ApiKey 对应明文为占位演示串（sk_live_seed...DEMO），本文件只登记其
--    SHA-256 摘要；状态置为 4=已撤销、plaintext_one_shot=0（已消费），
--    任何链路（python-sandbox 校验/管理端展示）均不可据此恢复或发起有效调用。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO client_app
    (id, client_code, client_name, description, owner_user_id, status, remark, create_by, update_by)
VALUES
    (1, 'demo-client', '示例客户端（种子）', '初始化演示客户端，非真实业务方', 1, 1,
     '种子数据；可安全删除', 'system', 'system');

INSERT IGNORE INTO client_api_key
    (id, name, client_id, bound_user_id, key_hash, key_prefix, key_suffix_mask,
     status, plaintext_one_shot, remark, create_by, update_by)
VALUES
    (1, '示例ApiKey（已撤销，不可调用）', 1, 1,
     '81a6fb14dc3a1c2bb970ccf90d54984516e50c6db5c581fa2d2f798bf9551a65',
     'sk_live_seed', 'DEMO',
     4, 0, '种子演示元数据：状态=已撤销且明文消费标记=已消费，仅用于验证列表掩码展示', 'system', 'system');
