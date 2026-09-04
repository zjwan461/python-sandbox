-- =====================================================================
-- 005-llm-review-menu.sql — 大模型复检菜单与权限种子
-- 说明：
--   * 新增"大模型复检"菜单（挂在"调用记录"(id=25)同级，即"业务管理"目录下）；
--   * 新增按钮权限：llmreview:view / llmreview:edit / llmreview:export；
--   * 超级管理员 / 管理员 / 审计员 均可查看，仅管理员可编辑。
-- =====================================================================

USE sandbox;

-- ---------------------------------------------------------------------
-- 1. 新增菜单：大模型复检（挂在"业务管理"(id=20)目录下，与"调用记录"同级）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO admin_menu (id, parent_id, menu_type, menu_name, icon, sort_order, route_path, route_name, component, perms, create_by, update_by) VALUES
    (7, 20, 'C', '大模型复检', 'MagicStick', 60, '/business/llm-review', 'BusinessLlmReview', 'log/llm-review/index', 'llmreview:view', 'system', 'system');

-- ---------------------------------------------------------------------
-- 2. 新增按钮权限
-- ---------------------------------------------------------------------
INSERT IGNORE INTO admin_menu (id, parent_id, menu_type, menu_name, sort_order, perms, create_by, update_by) VALUES
    (71, 7, 'F', '复检编辑', 1, 'llmreview:edit',   'system', 'system'),
    (72, 7, 'F', '复检导出', 2, 'llmreview:export', 'system', 'system');

-- ---------------------------------------------------------------------
-- 3. 角色-菜单授权
-- ---------------------------------------------------------------------
-- 超级管理员 / 管理员：全部菜单与按钮
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, create_by)
SELECT 1, id, 'system' FROM admin_menu WHERE id IN (7, 71, 72);

INSERT IGNORE INTO admin_role_menu (role_id, menu_id, create_by)
SELECT 2, id, 'system' FROM admin_menu WHERE id IN (7, 71, 72);

-- 审计员：只读
INSERT IGNORE INTO admin_role_menu (role_id, menu_id, create_by) VALUES
    (4, 7, 'system');
