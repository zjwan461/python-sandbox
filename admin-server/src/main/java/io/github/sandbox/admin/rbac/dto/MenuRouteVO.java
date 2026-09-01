package io.github.sandbox.admin.rbac.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 动态路由菜单节点（T-0019，design.md §9.6：GET /menus/routes 返回当前用户可见菜单树）。
 *
 * <p>仅目录（M）与菜单（C）参与路由树；按钮（F）不进入路由，经 whoami 的
 * permissions 集合下发给前端 v-permission 指令。</p>
 */
@Data
public class MenuRouteVO {

    private Long id;

    /** 类型：M=目录 C=菜单 */
    private String menuType;

    private String menuName;

    private String icon;

    private Integer sortOrder;

    private String routePath;

    private String routeName;

    private String component;

    /** 是否外链：1=是 0=否 */
    private Integer isExternal;

    /** 是否缓存：1=是 0=否 */
    private Integer isCache;

    /** 是否可见：1=显示 0=隐藏（隐藏节点仍注册路由但不在侧栏显示） */
    private Integer isVisible;

    /** 权限标识（菜单型） */
    private String perms;

    private List<MenuRouteVO> children = new ArrayList<>();
}
