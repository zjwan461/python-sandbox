package io.github.sandbox.admin.log.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API 日志列表/详情视图（T-0032，FR-LOG-01/04）。
 *
 * <p>截断口径（FR-LOG-04）：python-sandbox 写入侧以 {@code ...(truncated)} 结尾标记截断，
 * 本视图派生 {@code requestParamsTruncated} 布尔明示前端，不伪装为完整内容。</p>
 */
@Data
public class ApiLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String traceId;

    private String sessionId;

    private String apiPath;

    private String httpMethod;

    /** 请求参数（JSON，可能被写入侧截断） */
    private String requestParams;

    /** 请求参数是否被截断（派生标记，FR-LOG-04） */
    private Boolean requestParamsTruncated;

    private Integer responseCode;

    private Long executionTime;

    private String clientIp;

    private LocalDateTime createdAt;

    // ===== schema/006 扩展列 =====

    private Long clientId;

    /** 客户端编码（富化，可空） */
    private String clientCode;

    private Long apiKeyId;

    /** ApiKey 标签：名称+前缀+掩码（富化，永不含明文） */
    private String apiKeyLabel;

    private Long ownerUserId;

    /** 归属用户名（富化，可空） */
    private String ownerUserName;

    private Integer rateLimitHit;

    private Long rateLimitRuleId;
}
