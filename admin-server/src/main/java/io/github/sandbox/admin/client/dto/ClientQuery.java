package io.github.sandbox.admin.client.dto;

import lombok.Data;

/**
 * 客户端分页查询条件（T-0028，FR-CLIENT-01：名称/编码/归属人/状态筛选）。
 */
@Data
public class ClientQuery {

    /** 客户端名称（模糊） */
    private String clientName;

    /** 客户端编码（模糊） */
    private String clientCode;

    /** 归属用户ID（精确；SELF 域由数据权限拦截器另行行过滤） */
    private Long ownerUserId;

    /** 状态：1=启用 0=停用（null=全部） */
    private Integer status;

    /** 排序字段：id / clientCode / clientName / createTime（默认 createTime） */
    private String orderBy;

    /** 是否升序（默认 false=倒序） */
    private Boolean asc = false;

    /** 页码（1起） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 20;
}
