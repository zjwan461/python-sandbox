package io.github.sandbox.admin.rbac.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色-菜单关联实体（表 admin_role_menu，联合主键，不继承 BaseEntity，与 schema/001 一致）。
 */
@Data
@TableName("admin_role_menu")
public class AdminRoleMenu implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 角色ID（admin_role.id） */
    private Long roleId;

    /** 菜单ID（admin_menu.id） */
    private Long menuId;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 创建人 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;
}
