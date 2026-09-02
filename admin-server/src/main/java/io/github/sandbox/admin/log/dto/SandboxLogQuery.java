package io.github.sandbox.admin.log.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 沙箱操作日志分页查询条件（T-0032，FR-LOG-02：时间、操作类型、结果、traceId、sessionId 筛选）。
 */
@Data
public class SandboxLogQuery {

    /** 时间范围-起（按 created_at） */
    private LocalDateTime beginTime;

    /** 时间范围-止 */
    private LocalDateTime endTime;

    /** 操作类型：PYTHON_EXEC / SHELL_EXEC / PIP_INSTALL / PIP_UNINSTALL / PIP_LIST */
    private String operationType;

    /** 执行结果：SUCCESS / FAILED */
    private String result;

    /** traceId（精确） */
    private String traceId;

    /** 会话ID（精确） */
    private String sessionId;

    /** 客户端 ID（精确） */
    private Long clientId;

    /** ApiKey ID（精确） */
    private Long apiKeyId;

    /** 归属用户 ID（精确；ALL 域专用） */
    private Long ownerUserId;

    /** 排序字段：id / createdAt / executionTime（默认 createdAt 倒序） */
    private String orderBy;

    /** 是否升序（默认 false=倒序） */
    private Boolean asc = false;

    /** 页码（1起） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 20;
}
