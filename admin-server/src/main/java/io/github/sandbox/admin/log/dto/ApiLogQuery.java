package io.github.sandbox.admin.log.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * API 日志分页查询条件（T-0032，FR-LOG-01：时间、ApiKey、客户端、用户、方法、路径、
 * 状态码、traceId、IP 筛选）。
 *
 * <p>普通用户 SELF 可见域由数据权限拦截器对 api_log.owner_user_id 行过滤自动实现（T-0021）。</p>
 */
@Data
public class ApiLogQuery {

    /** 时间范围-起（按 created_at） */
    private LocalDateTime beginTime;

    /** 时间范围-止 */
    private LocalDateTime endTime;

    /** 调用 ApiKey ID（精确） */
    private Long apiKeyId;

    /** 客户端 ID（精确） */
    private Long clientId;

    /** 归属用户 ID（精确；ALL 域专用，SELF 域被行过滤覆盖） */
    private Long ownerUserId;

    /** HTTP 方法（精确，如 POST） */
    private String httpMethod;

    /** 接口路径（模糊） */
    private String apiPath;

    /** 响应状态码（精确） */
    private Integer responseCode;

    /** traceId（精确） */
    private String traceId;

    /** 客户端 IP（精确） */
    private String clientIp;

    /** 会话ID（精确） */
    private String sessionId;

    /** 仅限流命中记录（true=rate_limit_hit=1；null=全部） */
    private Boolean rateLimitHit;

    /** 排序字段：id / createdAt / executionTime / responseCode（默认 createdAt 倒序，FR-LOG-05） */
    private String orderBy;

    /** 是否升序（默认 false=倒序） */
    private Boolean asc = false;

    /** 页码（1起） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 20;
}
