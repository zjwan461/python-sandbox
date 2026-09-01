-- =====================================================================
-- 002-client-apikey.sql — 客户端与 ApiKey schema（T-0003）
-- 依据：design.md §6.1、§6.2（默认决策 #1：明文不存库）；requirements.md §5.1、§5.3
-- 说明：
--   * client_app：客户端编码全局唯一；归属用户 owner_user_id 可空（默认决策 #4/#5）。
--   * client_api_key：不保存任何可恢复明文。持久化材料仅：
--       - key_hash          认证用不可逆摘要（SHA-256(hex)，python-sandbox 侧按摘要查表校验）
--       - key_prefix        密钥前缀（如 sk_live_ab12），外部识别用
--       - key_suffix_mask   后 4 位掩码，界面识别用
--       - plaintext_one_shot 一次性明文展示消费标记（1=尚未消费可展示一次，0=已消费永不返回）
--   * 限流命中不建独立表（默认决策 #2），见 006 脚本对 api_log 的扩展。
-- =====================================================================

USE sandbox;

-- ---------------------------------------------------------------------
-- 客户端应用表
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS client_app (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    client_code  VARCHAR(64)  NOT NULL COMMENT '客户端编码（全局唯一，对外可见标识）',
    client_name  VARCHAR(128) NOT NULL COMMENT '客户端名称（可重复，人读）',
    description  VARCHAR(255)          DEFAULT NULL COMMENT '描述',
    owner_user_id BIGINT               DEFAULT NULL COMMENT '归属用户（admin_user.id，可空=按客户端维度计，管理员背书）',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
    remark       VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by    VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    update_by    VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    deleted      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    UNIQUE KEY uk_client_app_code (client_code, deleted),
    INDEX idx_client_app_owner (owner_user_id),
    INDEX idx_client_app_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户端应用表';

-- ---------------------------------------------------------------------
-- 客户端 ApiKey 表（明文不入库）
-- 状态机：1启用 → 2停用(可回到1) / 3已过期(自然) / 4已撤销(不可逆)
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS client_api_key (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    name              VARCHAR(128) NOT NULL COMMENT 'ApiKey 名称（人读）',
    client_id         BIGINT       NOT NULL COMMENT '绑定客户端（client_app.id，必填）',
    bound_user_id     BIGINT                DEFAULT NULL COMMENT '绑定用户（admin_user.id，可空=按客户端维度计）',
    key_hash          CHAR(64)     NOT NULL COMMENT 'ApiKey 明文 SHA-256 摘要（hex 小写，64字符）；认证唯一依据，不可逆推明文',
    key_prefix        VARCHAR(32)  NOT NULL COMMENT '密钥前缀（如 sk_live_ab12），用于外部识别',
    key_suffix_mask   VARCHAR(8)   NOT NULL COMMENT '后 4 位掩码，用于界面识别',
    effective_time    DATETIME              DEFAULT NULL COMMENT '生效时间（NULL=立即生效）',
    expire_time       DATETIME              DEFAULT NULL COMMENT '过期时间（NULL=永不过期）',
    status            TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=启用 2=停用 3=已过期 4=已撤销（撤销不可逆）',
    rate_limit_exempt TINYINT      NOT NULL DEFAULT 0 COMMENT '限流白名单（无限流例外，FR-RATELIMIT-05）：1=跳过全部规则 0=正常判定',
    plaintext_one_shot TINYINT     NOT NULL DEFAULT 0 COMMENT '一次性明文展示消费标记：1=可展示一次 0=已消费/从未生成（列表与详情永不返回明文）',
    remark            VARCHAR(255)          DEFAULT NULL COMMENT '备注',
    create_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by         VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '创建人',
    update_by         VARCHAR(64)  NOT NULL DEFAULT 'system' COMMENT '更新人',
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    UNIQUE KEY uk_client_api_key_hash (key_hash),
    INDEX idx_client_api_key_client (client_id),
    INDEX idx_client_api_key_user (bound_user_id),
    INDEX idx_client_api_key_status (status),
    INDEX idx_client_api_key_prefix (key_prefix)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客户端ApiKey表（仅存非明文摘要/前缀/掩码，明文不入库）';
