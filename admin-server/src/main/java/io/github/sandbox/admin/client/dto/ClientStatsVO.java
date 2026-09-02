package io.github.sandbox.admin.client.dto;

import lombok.Data;

/**
 * 客户端统计卡片（T-0035，FR-CLIENT-05；design.md §10.3 GET /clients/{id}/stats）。
 *
 * <p>统计口径与当前客户端筛选范围一致：ApiKey 数按 client_id 聚合，
 * 活跃=状态为启用或停用（未撤销未过期）；调用次数按 api_log.client_id 聚合。</p>
 */
@Data
public class ClientStatsVO {

    /** ApiKey 总数（不含逻辑删除） */
    private long apiKeyCount;

    /** 活跃 ApiKey 数（启用/停用且未过期） */
    private long activeApiKeyCount;

    /** 今日调用次数 */
    private long todayCalls;

    /** 累计调用次数 */
    private long totalCalls;
}
