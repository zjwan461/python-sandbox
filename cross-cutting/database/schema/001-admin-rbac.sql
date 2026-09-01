-- =====================================================================
-- 001-admin-rbac.sql — 基础 RBAC schema（T-0002）
-- 依据：design.md §5.1、§5.2；requirements.md §5.1
-- 目标库：sandbox（沿用 utf8mb4 / utf8mb4_unicode_ci，蛇形命名）
-- 说明：
--   * 用户保留可空部门文本字段 dept_name，本轮不创建 admin_dept 表/部门树实体。
--   * 公共字段口径（BaseEntity，design.md §4.4）：id / create_time / update_time
--     / create_by / update_by / deleted（逻辑删除）。
--   * 主键策略：BIGINT AUTO_INCREMENT（与既有 api_log 风格一致，管理端业务表统一此风格）。
-- =====================================================================

USE sandbox;

-- ---------------------------------------------------------------------
-- 管理端用户表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_user (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username            VARCHAR(64)  NOT NULL COMMENT '登录用户名（全局唯一）',
    nickname            VARCHAR(64)           DEFAULT NULL COMMENT '昵称',
    email               VARCHAR(128)          DEFAULT NULL COMMENT '邮箱',
    phone               VARCHAR(32)           DEFAULT NULL COMMENT '手机号',
    avatar              VARCHAR(255)          DEFAULT NULL COMMENT '头像URL',
    password_hash       VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希（不存明文；BCrypt 自带盐，salt 列保留供未来非 BCrypt 方案扩展）',
    salt                VARCHAR(64)           DEFAULT NULL COMMENT '盐（BCrypt 方案下可为空）',
    status              TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
    dept_name           VARCHAR(128)          DEFAULT NULL COMMENT '所属部门（可空文本字段；本轮不建部门树）',
    login_fail_count    INT          NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    lock_expire_time    DATETIME              DEFAULT NULL COMMENT '锁定到期时间（NULL 或早于当前时间=未锁定）',
    first_login         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否首次登录（强制改密）：1=是 0=否',
    last_login_time     DATETIME              DEFAULT NULL COMMENT '最后登录时间',
    remark              VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by           VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '创建人（登录前/种子=system）',
    update_by           VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    deleted             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    UNIQUE KEY uk_admin_user_username (username, deleted),
    INDEX idx_admin_user_status (status),
    INDEX idx_admin_user_lock (lock_expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端用户表';

-- ---------------------------------------------------------------------
-- 角色表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_role (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    role_name     VARCHAR(64) NOT NULL COMMENT '角色名称',
    role_key      VARCHAR(64) NOT NULL COMMENT '角色权限字符（全局唯一，如 admin/common/auditor）',
    sort_order    INT         NOT NULL DEFAULT 0 COMMENT '显示排序',
    status        TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
    built_in      TINYINT     NOT NULL DEFAULT 0 COMMENT '是否内置角色：1=内置不可删除 0=自定义',
    remark        VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by     VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
    update_by     VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '更新人',
    deleted       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    UNIQUE KEY uk_admin_role_key (role_key, deleted),
    INDEX idx_admin_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端角色表';

-- ---------------------------------------------------------------------
-- 菜单/权限表（目录、菜单、按钮三类节点，树形）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_menu (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    parent_id     BIGINT      NOT NULL DEFAULT 0 COMMENT '父级菜单ID（0=根）',
    menu_type     VARCHAR(8)  NOT NULL COMMENT '类型：M=目录 C=菜单 F=按钮',
    menu_name     VARCHAR(64) NOT NULL COMMENT '菜单/权限名称',
    icon          VARCHAR(64)          DEFAULT NULL COMMENT '图标',
    sort_order    INT         NOT NULL DEFAULT 0 COMMENT '同级排序',
    route_path    VARCHAR(200)         DEFAULT NULL COMMENT '前端路由路径（菜单型）',
    route_name    VARCHAR(100)         DEFAULT NULL COMMENT '前端路由名称',
    component     VARCHAR(200)         DEFAULT NULL COMMENT '前端组件路径',
    is_external   TINYINT     NOT NULL DEFAULT 0 COMMENT '是否外链：1=是 0=否',
    is_cache      TINYINT     NOT NULL DEFAULT 1 COMMENT '是否缓存：1=是 0=否',
    is_visible    TINYINT     NOT NULL DEFAULT 1 COMMENT '是否可见：1=显示 0=隐藏',
    perms         VARCHAR(100)         DEFAULT NULL COMMENT '权限字符（如 apikey:edit；按钮必填，目录可空）',
    status        TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
    create_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by     VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
    update_by     VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '更新人',
    deleted       TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    INDEX idx_admin_menu_parent (parent_id),
    INDEX idx_admin_menu_perms (perms),
    INDEX idx_admin_menu_type (menu_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端菜单与按钮权限表';

-- ---------------------------------------------------------------------
-- 用户-角色关联（多对多，联合主键）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_user_role (
    user_id     BIGINT   NOT NULL COMMENT '用户ID（admin_user.id）',
    role_id     BIGINT   NOT NULL COMMENT '角色ID（admin_role.id）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    create_by   VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
    PRIMARY KEY (user_id, role_id),
    INDEX idx_admin_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

-- ---------------------------------------------------------------------
-- 角色-菜单关联（多对多，联合主键）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_role_menu (
    role_id     BIGINT   NOT NULL COMMENT '角色ID（admin_role.id）',
    menu_id     BIGINT   NOT NULL COMMENT '菜单ID（admin_menu.id）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    create_by   VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
    PRIMARY KEY (role_id, menu_id),
    INDEX idx_admin_role_menu_menu (menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色菜单关联表';
