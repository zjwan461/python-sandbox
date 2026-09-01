package io.github.sandbox.admin.rbac.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.security.AdminLoginUser;
import io.github.sandbox.admin.common.security.SecurityUtils;
import io.github.sandbox.admin.rbac.dto.MenuRouteVO;
import io.github.sandbox.admin.rbac.dto.MenuTreeVO;
import io.github.sandbox.admin.rbac.entity.AdminMenu;
import io.github.sandbox.admin.rbac.mapper.AdminMenuMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 菜单管理服务（T-0019）：菜单树 CRUD + 当前用户动态路由树（按角色过滤）。
 */
@Service
@RequiredArgsConstructor
public class AdminMenuService {

    private static final String TYPE_DIR = "M";
    private static final String TYPE_MENU = "C";
    private static final String TYPE_BUTTON = "F";

    private final AdminMenuMapper adminMenuMapper;

    /** 全量菜单树（管理页用，含按钮） */
    public List<MenuTreeVO> tree() {
        List<AdminMenu> all = adminMenuMapper.selectList(Wrappers.<AdminMenu>lambdaQuery()
                .orderByAsc(AdminMenu::getParentId).orderByAsc(AdminMenu::getSortOrder));
        return buildTreeVO(all);
    }

    /** 当前登录用户可见动态路由树（仅 M/C，按角色授权过滤） */
    public List<MenuRouteVO> routes() {
        AdminLoginUser current = SecurityUtils.getLoginUser();
        List<AdminMenu> authorized = adminMenuMapper.selectMenusByUserId(current.getUserId());
        List<AdminMenu> routeNodes = authorized.stream()
                .filter(m -> TYPE_DIR.equals(m.getMenuType()) || TYPE_MENU.equals(m.getMenuType()))
                .sorted(Comparator.comparing(AdminMenu::getParentId).thenComparing(AdminMenu::getSortOrder))
                .collect(Collectors.toList());
        return buildRouteTree(routeNodes, 0L);
    }

    /** 新增菜单 */
    public Long create(AdminMenu menu) {
        validateMenu(menu, null);
        adminMenuMapper.insert(menu);
        return menu.getId();
    }

    /** 编辑菜单 */
    public void update(Long id, AdminMenu menu) {
        AdminMenu existing = adminMenuMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "菜单不存在");
        }
        if (id.equals(menu.getParentId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "父级不能为自身");
        }
        validateMenu(menu, id);
        menu.setId(id);
        adminMenuMapper.updateById(menu);
    }

    /** 删除菜单（存在子节点拒绝） */
    public void delete(Long id) {
        AdminMenu existing = adminMenuMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "菜单不存在");
        }
        long children = adminMenuMapper.selectCount(Wrappers.<AdminMenu>lambdaQuery()
                .eq(AdminMenu::getParentId, id));
        if (children > 0) {
            throw new BusinessException(ErrorCode.MENU_HAS_CHILDREN);
        }
        adminMenuMapper.deleteById(id);
        // 关联授权由角色管理侧维护；此处删除后残留 role_menu 引用不影响鉴权（按 menu 存在性收敛）
    }

    /** 某角色已授权菜单ID列表 */
    public List<Long> menuIdsByRole(Long roleId) {
        return adminMenuMapper.selectList(Wrappers.<AdminMenu>lambdaQuery()
                        .inSql(AdminMenu::getId,
                                "SELECT menu_id FROM admin_role_menu WHERE role_id = " + roleId))
                .stream().map(AdminMenu::getId).toList();
    }

    // ===================== internal =====================

    private void validateMenu(AdminMenu menu, Long selfId) {
        String type = menu.getMenuType();
        if (!TYPE_DIR.equals(type) && !TYPE_MENU.equals(type) && !TYPE_BUTTON.equals(type)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "菜单类型必须为 M/C/F");
        }
        if (TYPE_BUTTON.equals(type) && (menu.getPerms() == null || menu.getPerms().isBlank())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "按钮型必须提供权限字符 perms");
        }
        if (menu.getParentId() == null) {
            menu.setParentId(0L);
        }
        if (menu.getParentId() != 0 && adminMenuMapper.selectById(menu.getParentId()) == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "父级菜单不存在");
        }
    }

    private List<MenuTreeVO> buildTreeVO(List<AdminMenu> flat) {
        Map<Long, MenuTreeVO> voMap = new LinkedHashMap<>();
        for (AdminMenu m : flat) {
            MenuTreeVO vo = new MenuTreeVO();
            BeanUtils.copyProperties(m, vo);
            voMap.put(m.getId(), vo);
        }
        List<MenuTreeVO> roots = new ArrayList<>();
        for (MenuTreeVO vo : voMap.values()) {
            MenuTreeVO parent = vo.getParentId() == null ? null : voMap.get(vo.getParentId());
            if (parent != null) {
                parent.getChildren().add(vo);
            } else {
                roots.add(vo);
            }
        }
        return roots;
    }

    private List<MenuRouteVO> buildRouteTree(List<AdminMenu> routeNodes, Long parentId) {
        Map<Long, AdminMenu> byId = routeNodes.stream()
                .collect(Collectors.toMap(AdminMenu::getId, Function.identity(), (a, b) -> a));
        List<MenuRouteVO> result = new ArrayList<>();
        for (AdminMenu node : routeNodes) {
            Long pid = node.getParentId() == null ? 0L : node.getParentId();
            boolean isRootOfVisibleTree = parentId.equals(pid)
                    || (pid != 0 && !byId.containsKey(pid)); // 授权收敛导致的孤儿节点提升为根
            if (isRootOfVisibleTree) {
                MenuRouteVO vo = toRouteVO(node);
                vo.setChildren(buildRouteTree(routeNodes, node.getId()));
                result.add(vo);
            }
        }
        return result;
    }

    private MenuRouteVO toRouteVO(AdminMenu m) {
        MenuRouteVO vo = new MenuRouteVO();
        vo.setId(m.getId());
        vo.setMenuType(m.getMenuType());
        vo.setMenuName(m.getMenuName());
        vo.setIcon(m.getIcon());
        vo.setSortOrder(m.getSortOrder());
        vo.setRoutePath(m.getRoutePath());
        vo.setRouteName(m.getRouteName());
        vo.setComponent(m.getComponent());
        vo.setIsExternal(m.getIsExternal());
        vo.setIsCache(m.getIsCache());
        vo.setIsVisible(m.getIsVisible());
        vo.setPerms(m.getPerms());
        return vo;
    }
}
