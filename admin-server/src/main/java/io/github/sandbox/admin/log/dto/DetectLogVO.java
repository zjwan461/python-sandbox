package io.github.sandbox.admin.log.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * CodeGuard 模型推理检测记录列表/详情视图。
 */
@Data
public class DetectLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String traceId;

    private String sessionId;

    /** 送检代码片段原文 */
    private String codeSnippet;

    /** 代码字符数 */
    private Integer codeLength;

    /** 推理模型标识 */
    private String modelName;

    /** 模型判定标签：SAFE / DANGEROUS（服务异常为 null） */
    private String label;

    /** 判定危险标志：1=DANGEROUS 0=SAFE null=未取得结果 */
    private Integer dangerous;

    /** 模型原始输出文本 */
    private String rawOutput;

    /** 调用状态：OK / SERVICE_ERROR */
    private String detectStatus;

    /** 最终处置：ALLOW / BLOCK / FAIL_OPEN / FAIL_CLOSE */
    private String decision;

    /** 推理调用耗时（毫秒） */
    private Long latencyMs;

    /** 错误摘要（SERVICE_ERROR 时） */
    private String errorMessage;

    private LocalDateTime createdAt;

    // ===== 归属扩展（展示用派生字段） =====

    private Long clientId;

    private String clientCode;

    private Long apiKeyId;

    private String apiKeyLabel;

    private Long ownerUserId;

    private String ownerUserName;
}
