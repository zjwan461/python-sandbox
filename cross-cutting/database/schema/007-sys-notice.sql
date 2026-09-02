-- =====================================================================
-- 007-sys-notice.sql — 通知公告 schema（T-0040）
-- 依据：requirements.md FR-SYS-02~FR-SYS-03、§5.1；design.md §10.3
-- 目标库：sandbox（utf8mb4 / utf8mb4_unicode_ci，蛇形命名）
-- 说明：
--   * sys_notice：公告标题、内容、生效/失效时间、置顶、状态（发布/下线）、发布人。
--   * sys_notice_read：用户已读状态（追加式，联合唯一 公告+用户）。
--   * 公告与 admin_op_log / admin_login_log 严格分离，不混用（T-0040 验收）。
--   * 公共字段口径同 BaseEntity（id/create_time/update_time/create_by/update_by/deleted）。
-- =====================================================================

USE sandbox;

-- ---------------------------------------------------------------------
-- 公告表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_notice (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    title          VARCHAR(200) NOT NULL COMMENT '公告标题',
    content        TEXT         NOT NULL COMMENT '公告内容',
    effective_time DATETIME              DEFAULT NULL COMMENT '生效时间（NULL=发布即生效）',
    expire_time    DATETIME              DEFAULT NULL COMMENT '失效时间（NULL=长期有效）',
    is_top         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否置顶：1=置顶 0=普通',
    status         TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=草稿（未发布） 1=已发布',
    publisher_id   BIGINT                DEFAULT NULL COMMENT '发布人（admin_user.id；发布动作时回填）',
    publisher_name VARCHAR(64)           DEFAULT NULL COMMENT '发布人用户名（冗余展示）',
    publish_time   DATETIME              DEFAULT NULL COMMENT '发布时间',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by      VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    update_by      VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    deleted        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    INDEX idx_sys_notice_status (status),
    INDEX idx_sys_notice_effective (effective_time, expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知公告表（FR-SYS-02）';

-- ---------------------------------------------------------------------
-- 公告用户已读状态表（追加式；不存在行=未读）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_notice_read (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    notice_id   BIGINT   NOT NULL COMMENT '公告ID（sys_notice.id）',
    user_id     BIGINT   NOT NULL COMMENT '用户ID（admin_user.id）',
    read_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '已读时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    create_by   VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
    UNIQUE KEY uk_sys_notice_read (notice_id, user_id),
    INDEX idx_sys_notice_read_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告用户已读状态表（FR-SYS-03）';
