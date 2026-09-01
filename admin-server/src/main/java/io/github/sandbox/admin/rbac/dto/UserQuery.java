package io.github.sandbox.admin.rbac.dto;

import lombok.Data;

/**
 * 用户分页查询条件（T-0018：分页/筛选）。
 */
@Data
public class UserQuery {

    /** 用户名（模糊） */
    private String username;

    /** 昵称（模糊） */
    private String nickname;

    /** 状态：1=启用 0=停用（null=全部） */
    private Integer status;

    /** 部门文本（模糊） */
    private String deptName;

    /** 页码（1起） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 20;
}
