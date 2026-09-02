package io.github.sandbox.admin.rbac.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.security.DataScopes;
import io.github.sandbox.admin.rbac.entity.AdminMenu;
import io.github.sandbox.admin.rbac.entity.AdminRole;
import io.github.sandbox.admin.rbac.entity.AdminRoleMenu;
import io.github.sandbox.admin.rbac.entity.AdminUserRole;
import io.github.sandbox.admin.rbac.mapper.AdminMenuMapper;
import io.github.sandbox.admin.rbac.mapper.AdminRoleMapper;
import io.github.sandbox.admin.rbac.mapper.AdminRoleMenuMapper;
import io.github.sandbox.admin.rbac.mapper.AdminUserMapper;
import io.github.sandbox.admin.rbac.mapper.AdminUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 角色管理服务（T-0019 后端部分）：列表、增改删、启停用、分配菜单权限。
 *
 * <p>保护规则：内置角色（built_in=1）不可删除、不可修改权限字符 roleKey；
 * 删除前校验用户引用。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    private final AdminRoleMapper adminRoleMapper;
    private final AdminRoleMenuMapper adminRoleMenuMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final AdminUserMapper adminUserMapper;
    private final AdminMenuMapper adminMenuMapper;

    /** 分页列表 */
    public PageResult<AdminRole> page(String roleName, String roleKey, Integer status, long pageNum, long pageSize) {
        LambdaQueryWrapper<AdminRole> wrapper = Wrappers.<AdminRole>lambdaQuery()
                .like(StringUtils.hasText(roleName), AdminRole::getRoleName, roleName)
                .like(StringUtils.hasText(roleKey), AdminRole::getRoleKey, roleKey)
                .eq(status != null, AdminRole::getStatus, status)
                .orderByAsc(AdminRole::getSortOrder);
        Page<AdminRole> page = adminRoleMapper.selectPage(
                new Page<>(Math.max(1, pageNum), Math.min(Math.max(1, pageSize), 200)), wrapper);
        return PageResult.of(page);
    }

    /** 全量启用角色（下拉用） */
    public List<AdminRole> listEnabled() {
        return adminRoleMapper.selectList(Wrappers.<AdminRole>lambdaQuery()
                .eq(AdminRole::getStatus, 1).orderByAsc(AdminRole::getSortOrder));
    }

    /** 新增角色 */
    @Transactional(rollbackFor = Exception.class)
    public Long create(AdminRole role) {
        validateKeyUnique(role.getRoleKey(), null);
        role.setId(null);
        role.setBuiltIn(0); // 新建一律非内置
        role.setStatus(role.getStatus() == null ? 1 : role.getStatus());
        role.setSortOrder(role.getSortOrder() == null ? 0 : role.getSortOrder());
        adminRoleMapper.insert(role);
        return role.getId();
    }

    /** 编辑角色（内置角色禁改 roleKey） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AdminRole role) {
        AdminRole existing = requireRole(id);
        if (existing.getBuiltIn() != null && existing.getBuiltIn() == 1
                && StringUtils.hasText(role.getRoleKey())
                && !existing.getRoleKey().equals(role.getRoleKey())) {
            throw new BusinessException(ErrorCode.BUILT_IN_ROLE_PROTECTED, "内置角色不允许修改权限字符");
        }
        if (StringUtils.hasText(role.getRoleKey())) {
            validateKeyUnique(role.getRoleKey(), id);
        }
        role.setId(id);
        role.setBuiltIn(null); // 防止改内置标记
        adminRoleMapper.updateById(role);
        refreshSessionSnapshots(id);
    }

    /** 删除角色（内置不可删；被用户引用不可删；级联清理授权） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AdminRole existing = requireRole(id);
        if (existing.getBuiltIn() != null && existing.getBuiltIn() == 1) {
            throw new BusinessException(ErrorCode.BUILT_IN_ROLE_PROTECTED);
        }
        List<Long> boundUsers = adminUserRoleMapper.selectUserIdsByRoleId(id);
        if (!boundUsers.isEmpty()) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE);
        }
        adminRoleMenuMapper.delete(Wrappers.<AdminRoleMenu>lambdaQuery().eq(AdminRoleMenu::getRoleId, id));
        adminRoleMapper.deleteById(id);
    }

    /** 启停用（T-0039 增强：内置角色降级保护——超级管理员角色不可停用即"降级为无管理权限"） */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态仅支持 0=停用 1=启用");
        }
        AdminRole existing = requireRole(id);
        if (status == 0 && existing.getBuiltIn() != null && existing.getBuiltIn() == 1
                && DataScopes.ROLE_SUPERADMIN.equals(existing.getRoleKey())) {
            throw new BusinessException(ErrorCode.BUILT_IN_ROLE_PROTECTED,
                    "超级管理员角色不可停用（内置角色降级保护，FR-ROLE-04）");
        }
        AdminRole update = new AdminRole();
        update.setId(id);
        update.setStatus(status);
        adminRoleMapper.updateById(update);
        refreshSessionSnapshots(id);
    }

    /**
     * 分配菜单权限（全量替换）。
     *
     * <p>T-0039 增强：内置超级管理员角色不允许被配置为"无系统管理权限"——
     * 提交的菜单集合必须仍覆盖全部现有 menu:* 与 user:* 按钮/菜单授权（防止变相降级）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void assignMenus(Long id, List<Long> menuIds) {
        AdminRole role = requireRole(id);
        if (role.getBuiltIn() != null && role.getBuiltIn() == 1
                && DataScopes.ROLE_SUPERADMIN.equals(role.getRoleKey())) {
            List<Long> current = adminRoleMenuMapper.selectMenuIdsByRoleId(id);
            Set<Long> submitted = new java.util.HashSet<>(menuIds == null ? List.of() : menuIds);
            for (Long menuId : current) {
                AdminMenu m = adminMenuMapper.selectById(menuId);
                if (m != null && m.getPerms() != null
                        && (m.getPerms().startsWith("menu:") || m.getPerms().startsWith("user:"))
                        && !submitted.contains(menuId)) {
                    throw new BusinessException(ErrorCode.BUILT_IN_ROLE_PROTECTED,
                            "超级管理员角色不能被移除用户/菜单管理权限（内置角色降级保护，FR-ROLE-04）");
                }
            }
        }
        adminRoleMenuMapper.delete(Wrappers.<AdminRoleMenu>lambdaQuery().eq(AdminRoleMenu::getRoleId, id));
        if (menuIds != null) {
            for (Long menuId : menuIds.stream().distinct().toList()) {
                AdminRoleMenu rm = new AdminRoleMenu();
                rm.setRoleId(id);
                rm.setMenuId(menuId);
                adminRoleMenuMapper.insert(rm);
            }
        }
        refreshSessionSnapshots(id);
    }

    /** 角色已授权菜单ID列表 */
    public List<Long> menuIds(Long roleId) {
        requireRole(roleId);
        return adminRoleMenuMapper.selectMenuIdsByRoleId(roleId);
    }

    // ===================== internal =====================

    private AdminRole requireRole(Long id) {
        AdminRole role = adminRoleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private void validateKeyUnique(String roleKey, Long excludeId) {
        if (!StringUtils.hasText(roleKey)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "角色权限字符不能为空");
        }
        boolean exists = adminRoleMapper.selectCount(Wrappers.<AdminRole>lambdaQuery()
                .eq(AdminRole::getRoleKey, roleKey)
                .ne(excludeId != null, AdminRole::getId, excludeId)) > 0;
        if (exists) {
            throw new BusinessException(ErrorCode.ROLE_KEY_EXISTS);
        }
    }

    /** 角色权限变化后，作废受影响用户的会话（强制重新登录获取新快照） */
    private void refreshSessionSnapshots(Long roleId) {
        List<Long> userIds = adminUserRoleMapper.selectList(Wrappers.<AdminUserRole>lambdaQuery()
                        .eq(AdminUserRole::getRoleId, roleId))
                .stream().map(AdminUserRole::getUserId).toList();
        for (Long userId : userIds) {
            try {
                StpUtil.logout(userId);
            } catch (Exception ignored) {
                // 个别账号登出失败不影响整体
            }
        }
    }
}
