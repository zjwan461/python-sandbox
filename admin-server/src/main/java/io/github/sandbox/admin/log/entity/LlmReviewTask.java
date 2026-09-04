package io.github.sandbox.admin.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 大模型复检任务实体（表 llm_review_task）。
 *
 * <p>每条记录对应一次对小模型推理结果的复检任务，包含：
 * <ul>
 *   <li>任务状态控制（PENDING/RUNNING/SUCCESS/FAILED/CANCELLED）</li>
 *   <li>大模型调用信息（provider/model/request/response/label/explanation）</li>
 *   <li>人工复核结果（覆盖大模型判定，作为最终真相）</li>
 * </ul>
 *
 * <p>字段口径见 cross-cutting/database/schema/009-llm-review-task.sql。</p>
 */
@Data
@TableName("llm_review_task")
public class LlmReviewTask implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联 codeguard_detect_log.id（小模型推理记录） */
    private Long detectLogId;

    /** 任务状态：PENDING/RUNNING/SUCCESS/FAILED/CANCELLED */
    private String taskStatus;

    /** 重试次数 */
    private Integer retryCount;

    /** 最大重试次数 */
    private Integer maxRetry;

    // ===== 大模型调用信息 =====

    /** 大模型提供商（deepseek/qwen/openai 等） */
    private String llmProvider;

    /** 大模型名称 */
    private String llmModel;

    /** 发送给大模型的 prompt（JSON 格式） */
    private String llmRequest;

    /** 大模型原始响应 */
    private String llmResponse;

    /** 大模型判定标签：SAFE / DANGEROUS */
    private String llmLabel;

    /** 大模型对小模型判定结果的解释（为什么对/错） */
    private String llmExplanation;

    /** 大模型调用耗时（毫秒） */
    private Long llmLatencyMs;

    /** 大模型调用失败时的错误信息 */
    private String llmErrorMessage;

    // ===== 人工复核 =====

    /** 人工复核状态：NULL=未复核 / AGREED=同意大模型 / DISAGREED=不同意大模型 */
    private String humanReviewStatus;

    /** 人工最终判定标签：SAFE / DANGEROUS（覆盖大模型结果） */
    private String humanLabel;

    /** 人工复核备注 */
    private String humanRemark;

    /** 复核人（admin_user.id） */
    private Long reviewerId;

    /** 复核时间 */
    private LocalDateTime reviewTime;

    // ===== 任务控制 =====

    /** 计划执行时间（延迟调度用） */
    private LocalDateTime scheduledTime;

    /** 任务开始时间 */
    private LocalDateTime startTime;

    /** 任务结束时间 */
    private LocalDateTime endTime;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
