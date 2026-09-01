package io.github.sandbox.admin.common.security;

import cn.dev33.satoken.stp.StpInterface;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Sa-Token 权限/角色数据源（T-0014，design.md §4.2）。
 *
 * <p>不直接查库，读取登录时写入 Sa-Token Session 的 {@link AdminLoginUser} 快照，
 * 保证 {@code @SaCheckPermission} / {@code @SaCheckRole} 与踢下线机制一致；
 * 快照过期由 Sa-Token Session 生命周期管理，重新登录后自动刷新。</p>
 */
@Component
public class AdminStpInterface implements StpInterface {

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        AdminLoginUser user = SecurityUtils.getLoginUserQuietly();
        if (user == null || user.getPermissions() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(user.getPermissions());
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        AdminLoginUser user = SecurityUtils.getLoginUserQuietly();
        if (user == null || user.getRoles() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(user.getRoles());
    }
}
