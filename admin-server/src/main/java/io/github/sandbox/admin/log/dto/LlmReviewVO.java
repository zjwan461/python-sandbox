package io.github.sandbox.admin.log.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 大模型复检任务视图对象。
 */
@Data
public class LlmReviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 关联的检测记录 ID */
    private Long detectLogId;

    /** 任务状态：PENDING / RUNNING / SUCCESS / FAILED / CANCELLED */
    private String taskStatus;

    /** 重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetry;

    // ===== 小模型原始结果（来自 codeguard_detect_log）=====

    /** 代码片段 */
    private String codeSnippet;

    /** 小模型判定标签：SAFE / DANGEROUS */
    private String smallModelLabel;

    /** 小模型原始输出 */
    private String smallModelRawOutput;

    // ===== 大模型复检结果 =====

    /** 大模型提供商 */
    private String llmProvider;

    /** 大模型名称 */
    private String llmModel;

    /** 大模型判定标签：SAFE / DANGEROUS */
    private String llmLabel;

    /** 大模型解释 */
    private String llmExplanation;

    /** 大模型调用耗时（毫秒） */
    private Long llmLatencyMs;

    /** 大模型调用错误信息 */
    private String llmErrorMessage;

    // ===== 人工复核 =====

    /** 人工复核状态：AGREED / DISAGREED / null */
    private String humanReviewStatus;

    /** 人工最终判定标签 */
    private String humanLabel;

    /** 人工复核备注 */
    private String humanRemark;

    /** 复核人 ID */
    private Long reviewerId;

    /** 复核人名称 */
    private String reviewerName;

    /** 复核时间 */
    private LocalDateTime reviewTime;

    // ===== 时间戳 =====

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
