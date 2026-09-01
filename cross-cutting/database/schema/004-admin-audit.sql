-- =====================================================================
-- 004-admin-audit.sql — 登录日志与操作审计 schema（T-0005）
-- 依据：design.md §5.2（默认决策 #12：记录主键 + 对象名/编码）；requirements.md FR-AUDIT-01~05
-- 说明：
--   * 两类日志均为"只追加"业务口径：表不含 update/deleted 字段，管理端不开放
--     修改与删除接口（FR-AUDIT-05）。
--   * admin_login_log 覆盖：成功、失败、锁定与原因。
--   * admin_op_log 覆盖：新增(add)、编辑(edit)、删除(delete)、启停(disable/enable)、
--     撤销(revoke)、重置(reset)、强销(force) 等写操作。
-- =====================================================================

USE sandbox;

-- ---------------------------------------------------------------------
-- 管理端登录日志
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_login_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    username       VARCHAR(64) NOT NULL COMMENT '登录用户名',
    user_id        BIGINT               DEFAULT NULL COMMENT '用户ID（成功或可识别账号时填充）',
    login_type     VARCHAR(16) NOT NULL DEFAULT 'PASSWORD' COMMENT '登录方式：PASSWORD / REMEMBER_ME / INTERNAL',
    result         VARCHAR(16) NOT NULL COMMENT '结果：SUCCESS / FAIL / LOCKED',
    fail_reason    VARCHAR(128)         DEFAULT NULL COMMENT '失败/锁定原因：BAD_CREDENTIALS / CAPTCHA_ERROR / ACCOUNT_DISABLED / ACCOUNT_LOCKED 等',
    ip             VARCHAR(64)          DEFAULT NULL COMMENT '来源IP',
    user_agent     VARCHAR(512)         DEFAULT NULL COMMENT '浏览器UA',
    login_time     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_admin_login_log_username (username),
    INDEX idx_admin_login_log_result (result),
    INDEX idx_admin_login_log_time (login_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端登录日志（只追加，不开放修改/删除）';

-- ---------------------------------------------------------------------
-- 管理端操作日志（写操作审计）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_op_log (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    operator_id    BIGINT      NOT NULL COMMENT '操作人ID（admin_user.id）',
    operator_name  VARCHAR(64) NOT NULL COMMENT '操作人用户名（冗余，保证审计可读性）',
    module         VARCHAR(64) NOT NULL COMMENT '模块：user / role / menu / client / apikey / ratelimit / session / bridge(对接网关) / sysconfig 等',
    operation_type VARCHAR(16) NOT NULL COMMENT '操作类型：add / edit / delete / enable / disable / revoke / reset / force / login-相关联动等',
    target_id      VARCHAR(64)          DEFAULT NULL COMMENT '目标对象主键（统一字符串承载，兼容 sessionId 等非数字主键）',
    target_name    VARCHAR(200)         DEFAULT NULL COMMENT '目标对象名/编码（默认决策 #12：主键+对象名）',
    change_summary TEXT                 DEFAULT NULL COMMENT '关键字段变更摘要（JSON：字段->旧值/新值）',
    result         VARCHAR(16) NOT NULL DEFAULT 'SUCCESS' COMMENT '结果：SUCCESS / FAIL',
    fail_reason    VARCHAR(255)         DEFAULT NULL COMMENT '失败原因',
    ip             VARCHAR(64)          DEFAULT NULL COMMENT '来源IP',
    user_agent     VARCHAR(512)         DEFAULT NULL COMMENT '浏览器UA',
    trace_id       VARCHAR(64)          DEFAULT NULL COMMENT '链路追踪ID（X-Trace-Id，沿用既有透传）',
    op_time        DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    INDEX idx_admin_op_log_operator (operator_id),
    INDEX idx_admin_op_log_module (module, operation_type),
    INDEX idx_admin_op_log_target (target_id),
    INDEX idx_admin_op_log_time (op_time),
    INDEX idx_admin_op_log_trace (trace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端操作审计日志（只追加，不开放修改/删除）';
