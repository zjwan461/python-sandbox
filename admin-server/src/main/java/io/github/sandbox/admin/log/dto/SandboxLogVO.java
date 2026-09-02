package io.github.sandbox.admin.log.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 沙箱操作日志列表/详情视图（T-0032，FR-LOG-02/04）。
 *
 * <p>截断字段（operationContent/stdout/stderr）以写入侧 {@code ...(truncated)} 后缀派生布尔标记。</p>
 */
@Data
public class SandboxLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private String traceId;

    private String sessionId;

    /** 操作类型：PYTHON_EXEC / SHELL_EXEC / PIP_INSTALL / PIP_UNINSTALL / PIP_LIST */
    private String operationType;

    private String operationContent;

    private Boolean operationContentTruncated;

    /** 执行结果：SUCCESS / FAILED */
    private String result;

    private Integer exitCode;

    private String stdout;

    private Boolean stdoutTruncated;

    private String stderr;

    private Boolean stderrTruncated;

    private Long executionTime;

    private String errorMessage;

    private LocalDateTime createdAt;

    // ===== schema/006 扩展列 =====

    private Long clientId;

    private String clientCode;

    private Long apiKeyId;

    private String apiKeyLabel;

    private Long ownerUserId;

    private String ownerUserName;
}
