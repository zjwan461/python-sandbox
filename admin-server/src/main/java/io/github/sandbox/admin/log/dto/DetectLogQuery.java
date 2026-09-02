package io.github.sandbox.admin.log.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * CodeGuard 模型推理检测记录分页查询条件：
 * 时间、判定标签、处置决策、调用状态、traceId、sessionId、归属筛选。
 */
@Data
public class DetectLogQuery {

    /** 时间范围-起（按 created_at） */
    private LocalDateTime beginTime;

    /** 时间范围-止 */
    private LocalDateTime endTime;

    /** 模型判定标签：SAFE / DANGEROUS */
    private String label;

    /** 最终处置：ALLOW / BLOCK / FAIL_OPEN / FAIL_CLOSE */
    private String decision;

    /** 调用状态：OK / SERVICE_ERROR */
    private String detectStatus;

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

    /** 排序字段：id / createdAt / latencyMs（默认 createdAt 倒序） */
    private String orderBy;

    /** 是否升序（默认 false=倒序） */
    private Boolean asc = false;

    /** 页码（1起） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 20;
}
