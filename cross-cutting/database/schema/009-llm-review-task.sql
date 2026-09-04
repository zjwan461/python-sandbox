-- =====================================================================
-- 009-llm-review-task.sql — 大模型复检任务表
-- 说明：
--   * admin-server 异步调用大模型（DeepSeek/Qwen 等）对小模型推理结果进行复检；
--   * 每条记录对应一次复检任务，包含任务状态、大模型判定结果、解释、人工复核结果；
--   * 支持任务中断、重启、重试；人工可在页面修改复核结果（最终真相）。
-- =====================================================================

USE sandbox;

CREATE TABLE IF NOT EXISTS llm_review_task (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    detect_log_id           BIGINT       NOT NULL COMMENT '关联 codeguard_detect_log.id（小模型推理记录）',
    task_status             VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '任务状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED',
    retry_count             INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    max_retry               INT          NOT NULL DEFAULT 3 COMMENT '最大重试次数',

    -- 大模型调用信息
    llm_provider            VARCHAR(64)           DEFAULT NULL COMMENT '大模型提供商（deepseek/qwen/openai 等）',
    llm_model               VARCHAR(128)          DEFAULT NULL COMMENT '大模型名称',
    llm_request             MEDIUMTEXT            DEFAULT NULL COMMENT '发送给大模型的 prompt（JSON 格式）',
    llm_response            MEDIUMTEXT            DEFAULT NULL COMMENT '大模型原始响应',
    llm_label               VARCHAR(16)           DEFAULT NULL COMMENT '大模型判定标签：SAFE / DANGEROUS',
    llm_explanation         TEXT                  DEFAULT NULL COMMENT '大模型对小模型判定结果的解释（为什么对/错）',
    llm_latency_ms          BIGINT                DEFAULT NULL COMMENT '大模型调用耗时（毫秒）',
    llm_error_message       VARCHAR(512)          DEFAULT NULL COMMENT '大模型调用失败时的错误信息',

    -- 人工复核
    human_review_status     VARCHAR(16)           DEFAULT NULL COMMENT '人工复核状态：NULL=未复核 / AGREED=同意大模型 / DISAGREED=不同意大模型',
    human_label             VARCHAR(16)           DEFAULT NULL COMMENT '人工最终判定标签：SAFE / DANGEROUS（覆盖大模型结果）',
    human_remark            TEXT                  DEFAULT NULL COMMENT '人工复核备注',
    reviewer_id             BIGINT                DEFAULT NULL COMMENT '复核人（admin_user.id）',
    review_time             DATETIME              DEFAULT NULL COMMENT '复核时间',

    -- 任务控制
    scheduled_time          DATETIME              DEFAULT NULL COMMENT '计划执行时间（延迟调度用）',
    start_time              DATETIME              DEFAULT NULL COMMENT '任务开始时间',
    end_time                DATETIME              DEFAULT NULL COMMENT '任务结束时间',
    created_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE KEY uk_detect_log_id (detect_log_id),
    KEY idx_llm_review_status (task_status, scheduled_time),
    KEY idx_llm_review_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大模型复检任务表（对小模型推理结果进行二次校验）';
