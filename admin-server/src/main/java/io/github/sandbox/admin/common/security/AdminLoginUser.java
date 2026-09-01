package io.github.sandbox.admin.common.security;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

/**
 * Sa-Token 会话中缓存的登录用户快照（T-0014/T-0016）。
 *
 * <p>登录成功时写入 Sa-Token Session（键 {@link #SESSION_KEY}），
 * 供权限解析（StpInterface）、数据权限拦截器、审计填充等读取，避免每次请求回查数据库。</p>
 */
@Data
public class AdminLoginUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Sa-Token Session 中的存储键 */
    public static final String SESSION_KEY = "loginUser";

    /** 用户ID（admin_user.id，Sa-Token loginId） */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 角色权限字符集合（admin_role.role_key） */
    private List<String> roles;

    /** 按钮/接口权限码集合（admin_menu.perms） */
    private Set<String> permissions;

    /** 是否首次登录（强制改密标记） */
    private boolean firstLogin;

    /** 数据权限范围：ALL（超管/管理员/审计员）或 SELF（普通用户） */
    private String dataScope;

    public boolean isSuperAdmin() {
        return roles != null && roles.contains(DataScopes.ROLE_SUPERADMIN);
    }

    /** 数据权限是否为全部可见 */
    public boolean isAllScope() {
        return DataScopes.ALL.equals(dataScope);
    }
}
