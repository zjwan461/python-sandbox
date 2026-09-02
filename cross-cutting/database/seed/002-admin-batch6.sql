-- =====================================================================
-- 002-admin-batch6.sql — 批次6（P1/P2）种子增量（T-0041 / T-0042 / T-0043）
-- 依据：requirements.md FR-SYS-01~03、FR-USER-07；design.md §10.3
-- 前置：已执行 001-admin-seed.sql 与 schema/001~007。
-- 特性：幂等可重复执行（显式主键 + INSERT IGNORE），create_by/update_by=system。
-- 说明：本文件仅追加批次6新增的菜单/按钮权限与角色授权，
--       不修改 001 已定型的权限码与菜单（契约兼容）。
-- =====================================================================

USE sandbox;

-- ---------------------------------------------------------------------
-- 1. 新增菜单：系统设置（T-0041）、通知公告（T-0042）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO admin_menu (id, parent_id, menu_type, menu_name, icon, sort_order, route_path, route_name, component, perms, create_by, update_by) VALUES
    (5, 1, 'C', '系统设置', 'Tools', 40, '/system/config', 'SystemConfig', 'system/config/index', 'sysconfig:view', 'system', 'system'),
    (6, 1, 'C', '通知公告', 'Bell',  50, '/system/notice', 'SystemNotice', 'system/notice/index', 'notice:view',    'system', 'system');

-- ---------------------------------------------------------------------
-- 2. 新增按钮权限（动词收敛：view/add/edit/delete）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO admin_menu (id, parent_id, menu_type, menu_name, sort_order, perms, create_by, update_by) VALUES
    (106, 2, 'F', '用户导出', 6, 'user:export',     'system', 'system'),
    (107, 2, 'F', '用户导入', 7, 'user:import',     'system', 'system'),
    (131, 5, 'F', '设置更新', 1, 'sysconfig:edit',  'system', 'system'),
    (141, 6, 'F', '公告新增', 1, 'notice:add',      'system', 'system'),
    (142, 6, 'F', '公告编辑', 2, 'notice:edit',     'system', 'system'),
    (143, 6, 'F', '公告删除', 3, 'notice:delete',   'system', 'system');

-- ---------------------------------------------------------------------
-- 3. 角色-菜单授权增量
-- ---------------------------------------------------------------------
-- 3.1 超级管理员 / 管理员：全部新增菜单与按钮
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, create_by)
SELECT 1, id, 'system' FROM admin_menu WHERE id IN (5, 6, 106, 107, 131, 141, 142, 143);

INSERT IGNORE INTO admin_role_menu (role_id, menu_id, create_by)
SELECT 2, id, 'system' FROM admin_menu WHERE id IN (5, 6, 106, 107, 131, 141, 142, 143);

-- 3.2 审计员：系统设置与公告只读（sysconfig:view / notice:view，无写按钮）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, create_by) VALUES
    (4, 1, 'system'), (4, 5, 'system'), (4, 6, 'system');

-- 3.3 普通用户：仅公告阅读（登录后通栏/站内信投递，FR-SYS-03）
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, create_by) VALUES
    (3, 6, 'system');
