package io.github.sandbox.admin.ratelimit.dto;

import lombok.Data;

/**
 * 限流规则分页查询条件（T-0030）。
 */
@Data
public class RatelimitQuery {

    /** 维度：API_KEY / CLIENT / GLOBAL（null=全部） */
    private String dimension;

    /** 目标主键（精确） */
    private Long targetId;

    /** 窗口类型：MINUTE / HOUR / DAY（null=全部） */
    private String windowType;

    /** 状态：1=启用 0=停用（null=全部） */
    private Integer status;

    /** 排序字段：id / priority / createTime（默认 priority 升序、createTime 倒序） */
    private String orderBy;

    /** 是否升序（默认 false=倒序；orderBy=priority 时建议 true） */
    private Boolean asc = false;

    /** 页码（1起） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 20;
}
