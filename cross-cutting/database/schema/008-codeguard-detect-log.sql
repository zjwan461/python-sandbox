-- =====================================================================
-- 008-codeguard-detect-log.sql — CodeGuard 模型推理检测记录表
-- 说明：
--   * python-sandbox 每次调用模型推理服务（POST /detect）后异步落库一条记录，
--     含代码片段、模型判定（label/raw_output）、最终处置与服务可用性状态；
--   * 定位双重：① 调用审计（traceId/sessionId 关联 api_log）；
--              ② 数据回流——供 train/ 侧导出再训练样本（数据飞轮）；
--   * code_snippet 原文入库：属不可信用户代码文本，不作为可执行内容处理。
-- =====================================================================

USE sandbox;

CREATE TABLE IF NOT EXISTS codeguard_detect_log (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    trace_id            VARCHAR(64)           DEFAULT NULL COMMENT '请求追踪ID（关联 api_log.trace_id）',
    session_id          VARCHAR(64)           DEFAULT NULL COMMENT '沙箱会话ID',
    client_id           BIGINT                DEFAULT NULL COMMENT '归属客户端（client_app.id；匿名/无上下文为NULL）',
    api_key_id          BIGINT                DEFAULT NULL COMMENT '调用ApiKey（client_api_key.id）',
    owner_user_id       BIGINT                DEFAULT NULL COMMENT '归属用户（admin_user.id）',
    code_snippet        MEDIUMTEXT   NOT NULL COMMENT '送检代码片段原文（再训练样本源）',
    code_length         INT          NOT NULL DEFAULT 0 COMMENT '代码字符数',
    model_name          VARCHAR(128)          DEFAULT NULL COMMENT '推理模型标识（沙箱侧配置，便于多模型对比）',
    label               VARCHAR(16)           DEFAULT NULL COMMENT '模型判定标签：SAFE / DANGEROUS（服务异常为NULL）',
    dangerous           TINYINT               DEFAULT NULL COMMENT '判定危险标志：1=DANGEROUS 0=SAFE NULL=未取得结果',
    raw_output          VARCHAR(255)          DEFAULT NULL COMMENT '模型原始输出文本',
    detect_status       VARCHAR(16)  NOT NULL COMMENT '调用状态：OK=成功 SERVICE_ERROR=推理服务不可用/异常',
    decision            VARCHAR(16)  NOT NULL COMMENT '最终处置：ALLOW=放行 BLOCK=拦截 FAIL_OPEN=故障放行 FAIL_CLOSE=故障拒绝',
    latency_ms          BIGINT                DEFAULT NULL COMMENT '推理调用耗时（毫秒）',
    error_message       VARCHAR(512)          DEFAULT NULL COMMENT 'SERVICE_ERROR 时的错误摘要',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（与 api_log 等日志表列名口径一致）',
    KEY idx_cdl_trace (trace_id),
    KEY idx_cdl_label_time (label, created_at),
    KEY idx_cdl_dangerous_time (dangerous, created_at),
    KEY idx_cdl_client_time (client_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='CodeGuard模型推理检测记录（审计+再训练数据回流）';
