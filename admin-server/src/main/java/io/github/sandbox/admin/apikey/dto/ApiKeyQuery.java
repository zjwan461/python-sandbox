package io.github.sandbox.admin.apikey.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * ApiKey 分页查询条件（T-0029，FR-APIKEY-07：按客户端、用户、状态、过期时间范围筛选）。
 *
 * <p>普通用户的 SELF 数据权限由 {@code AdminDataPermissionHandler} 对
 * client_api_key 表自动行过滤，本查询条件仅表达业务筛选维度。</p>
 */
@Data
public class ApiKeyQuery {

    /** 名称（模糊） */
    private String name;

    /** 绑定客户端ID（精确） */
    private Long clientId;

    /** 绑定用户ID（精确） */
    private Long boundUserId;

    /** 状态：1=启用 2=停用 3=已过期 4=已撤销（null=全部） */
    private Integer status;

    /** 过期时间范围-起 */
    private LocalDateTime expireBegin;

    /** 过期时间范围-止 */
    private LocalDateTime expireEnd;

    /** 排序字段：id / name / createTime / expireTime（默认 createTime） */
    private String orderBy;

    /** 是否升序（默认 false=倒序） */
    private Boolean asc = false;

    /** 页码（1起） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 20;
}
