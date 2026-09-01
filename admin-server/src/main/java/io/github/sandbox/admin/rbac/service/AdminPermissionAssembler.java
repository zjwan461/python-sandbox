package io.github.sandbox.admin.rbac.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.sandbox.admin.common.security.AdminLoginUser;
import io.github.sandbox.admin.common.security.DataScopes;
import io.github.sandbox.admin.rbac.entity.AdminMenu;
import io.github.sandbox.admin.rbac.entity.AdminRole;
import io.github.sandbox.admin.rbac.mapper.AdminMenuMapper;
import io.github.sandbox.admin.rbac.mapper.AdminRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 登录用户权限快照组装（T-0014/T-0015）。
 *
 * <p>superadmin / admin 角色在登录时展开为全量启用菜单权限码（新增按钮在重新登录后
 * 自动获得，无需逐个授权）；auditor / common 严格按 admin_role_menu 授权聚合，
 * 从而 auditor 天然只持有 view 类权限码（只读语义由菜单授权收敛）。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminPermissionAssembler {

    private final AdminRoleMapper adminRoleMapper;
    private final AdminMenuMapper adminMenuMapper;

    /** 构建登录用户快照（角色、权限码、数据权限范围） */
    public AdminLoginUser assemble(Long userId, String username, String nickname, boolean firstLogin) {
        List<AdminRole> roles = adminRoleMapper.selectRolesByUserId(userId);
        List<String> roleKeys = roles.stream().map(AdminRole::getRoleKey).toList();

        Set<String> perms = new HashSet<>();
        boolean managementWide = roleKeys.contains(DataScopes.ROLE_SUPERADMIN)
                || roleKeys.contains(DataScopes.ROLE_ADMIN);
        if (managementWide) {
            // 管理域：全量启用权限码（登录时展开）
            adminMenuMapper.selectList(Wrappers.<AdminMenu>lambdaQuery()
                            .eq(AdminMenu::getStatus, 1))
                    .forEach(m -> addPerm(perms, m));
        } else {
            // 其余角色：严格按授权菜单聚合
            adminMenuMapper.selectMenusByUserId(userId).forEach(m -> addPerm(perms, m));
        }

        AdminLoginUser user = new AdminLoginUser();
        user.setUserId(userId);
        user.setUsername(username);
        user.setNickname(nickname);
        user.setRoles(roleKeys);
        user.setPermissions(perms);
        user.setFirstLogin(firstLogin);
        user.setDataScope(DataScopes.resolveScope(roleKeys));
        return user;
    }

    private void addPerm(Set<String> perms, AdminMenu menu) {
        if (menu.getPerms() != null && !menu.getPerms().isBlank()) {
            perms.add(menu.getPerms());
        }
    }
}
