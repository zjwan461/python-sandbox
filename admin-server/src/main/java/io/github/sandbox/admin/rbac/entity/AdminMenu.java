package io.github.sandbox.admin.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.sandbox.admin.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 管理端菜单/按钮权限实体（表 admin_menu，字段与 schema/001 严格一致）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_menu")
public class AdminMenu extends BaseEntity {

    /** 父级菜单ID（0=根） */
    private Long parentId;

    /** 类型：M=目录 C=菜单 F=按钮 */
    private String menuType;

    /** 菜单/权限名称 */
    private String menuName;

    /** 图标 */
    private String icon;

    /** 同级排序 */
    private Integer sortOrder;

    /** 前端路由路径（菜单型） */
    private String routePath;

    /** 前端路由名称 */
    private String routeName;

    /** 前端组件路径 */
    private String component;

    /** 是否外链：1=是 0=否 */
    private Integer isExternal;

    /** 是否缓存：1=是 0=否 */
    private Integer isCache;

    /** 是否可见：1=显示 0=隐藏 */
    private Integer isVisible;

    /** 权限字符（如 apikey:edit；按钮必填，目录可空） */
    private String perms;

    /** 状态：1=启用 0=停用 */
    private Integer status;
}
