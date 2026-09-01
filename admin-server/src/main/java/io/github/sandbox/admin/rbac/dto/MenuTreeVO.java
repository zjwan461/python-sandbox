package io.github.sandbox.admin.rbac.dto;

import io.github.sandbox.admin.rbac.entity.AdminMenu;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树节点（T-0019，管理页 /menus/tree 用，含按钮）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MenuTreeVO extends AdminMenu {

    private List<MenuTreeVO> children = new ArrayList<>();
}
