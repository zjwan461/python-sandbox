-- =====================================================================
-- 005-sys-config.sql — 管理端系统设置 KV schema（T-0006）
-- 依据：design.md §4.5、§7.6；requirements.md FR-SYS-01
-- 说明：
--   * 稳定键（config_key 唯一）+ 值类型约束（value_type）+ 说明。
--   * 业务接口只接受本表已登记的键（未识别键拒绝，见 admin-server T-0012 校验白名单）。
--   * is_built_in=1 的键不可通过管理界面删除，仅可更新值。
--   * 敏感业务凭证（内部共享密钥 admin.internal.token、ApiKey 明文等）
--     一律不作为普通系统设置保存（design.md §6.3：不进数据库）。
-- =====================================================================

USE sandbox;

CREATE TABLE IF NOT EXISTS sys_config (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    config_key   VARCHAR(100) NOT NULL COMMENT '稳定设置键（全局唯一，如 login.fail.threshold）',
    config_value VARCHAR(500) NOT NULL COMMENT '设置值（字符串存储，按 value_type 校验）',
    value_type   VARCHAR(16)  NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING / NUMBER / BOOLEAN / JSON',
    config_name  VARCHAR(100) NOT NULL COMMENT '设置项显示名称',
    description  VARCHAR(255)          DEFAULT NULL COMMENT '说明',
    is_built_in  TINYINT      NOT NULL DEFAULT 1 COMMENT '是否内置键：1=内置不可删 0=自定义',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by    VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    update_by    VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    UNIQUE KEY uk_sys_config_key (config_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端系统设置KV表';
