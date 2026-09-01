package io.github.sandbox.admin.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.sandbox.admin.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端角色实体（表 admin_role，字段与 schema/001 严格一致）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_role")
public class AdminRole extends BaseEntity {

    /** 角色名称 */
    private String roleName;

    /** 角色权限字符（全局唯一，如 admin/common/auditor） */
    private String roleKey;

    /** 显示排序 */
    private Integer sortOrder;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    /** 是否内置角色：1=内置不可删除 0=自定义（列 built_in） */
    private Integer builtIn;

    /** 备注 */
    private String remark;
}
