-- =====================================================================
-- 003-ratelimit.sql — 限流规则 schema（T-0004）
-- 依据：design.md §7.1、§7.5；requirements.md §5.1、FR-RATELIMIT-01~04
-- 说明：
--   * 支持同一目标叠加多条规则（不同窗口或不同阈值），任一命中即拒绝（FR-RATELIMIT-02）。
--   * 限流命中不建独立表：复用 api_log 的 rate_limit_hit 与 rate_limit_rule_id
--     字段（见 006-sandbox-log-extension.sql，默认决策 #2）。
--   * 白名单标志位承载于 client_api_key.rate_limit_exempt（见 002 脚本）。
--   * 全局默认限流值经 sys_config KV 提供（见 005 脚本），本表也可显式建
--     dimension='GLOBAL' 且 target_id=0 的规则承载（二者取一以实现批次 T-0036 定稿）。
--   * 下发模式：python-sandbox 启动加载 + 定时拉取（默认决策 #3），拉取条件
--     status=1 AND effective_time <= NOW() AND (expire_time IS NULL OR expire_time > NOW())。
-- =====================================================================

USE sandbox;

CREATE TABLE IF NOT EXISTS ratelimit_rule (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID（api_log.rate_limit_rule_id 关联此主键）',
    dimension      VARCHAR(16) NOT NULL COMMENT '维度：API_KEY=按ApiKey / CLIENT=按客户端 / GLOBAL=全局默认',
    target_id      BIGINT      NOT NULL DEFAULT 0 COMMENT '目标主键（API_KEY→client_api_key.id；CLIENT→client_app.id；GLOBAL→固定0）',
    window_type    VARCHAR(8)  NOT NULL COMMENT '窗口类型：MINUTE / HOUR / DAY',
    threshold      INT         NOT NULL COMMENT '窗口内最大请求数（正整数）',
    priority       INT         NOT NULL DEFAULT 100 COMMENT '优先级（数值越小越先判定；多规则叠加时任一命中即拒绝）',
    status         TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=停用',
    effective_time DATETIME             DEFAULT NULL COMMENT '生效时间（NULL=立即生效）',
    expire_time    DATETIME             DEFAULT NULL COMMENT '失效时间（NULL=永不失效）',
    remark         VARCHAR(255)         DEFAULT NULL COMMENT '备注',
    create_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    create_by      VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '创建人',
    update_by      VARCHAR(64) NOT NULL DEFAULT 'system' COMMENT '更新人',
    deleted        TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    UNIQUE KEY uk_ratelimit_rule_target (dimension, target_id, window_type, threshold, deleted),
    INDEX idx_ratelimit_rule_dimension_target (dimension, target_id),
    INDEX idx_ratelimit_rule_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='限流规则表（ApiKey/客户端/全局维度；命中记录复用 api_log 扩展字段）';
