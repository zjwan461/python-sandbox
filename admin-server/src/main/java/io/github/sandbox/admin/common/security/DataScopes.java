package io.github.sandbox.admin.common.security;

/**
 * 数据权限维度常量（T-0021，design.md §5.4/§5.5）。
 *
 * <p>本轮仅启用 ALL / SELF 两个维度：
 * superadmin、admin、auditor 命中 ALL；common（普通用户）命中 SELF。</p>
 */
public final class DataScopes {

    /** 全部可见 */
    public static final String ALL = "ALL";

    /** 仅本人归属数据可见（owner 解析口径见 cross-cutting/database/er-alignment.md §3） */
    public static final String SELF = "SELF";

    /** 超级管理员角色权限字符 */
    public static final String ROLE_SUPERADMIN = "superadmin";

    /** 管理员角色权限字符 */
    public static final String ROLE_ADMIN = "admin";

    /** 审计员角色权限字符（ALL 可见域，只读由按钮权限控制） */
    public static final String ROLE_AUDITOR = "auditor";

    /** 普通用户角色权限字符（SELF 可见域） */
    public static final String ROLE_COMMON = "common";

    private DataScopes() {
    }

    /** 按角色权限字符集合解析数据权限范围：含 superadmin/admin/auditor 任一即 ALL，否则 SELF */
    public static String resolveScope(java.util.Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return SELF;
        }
        if (roles.contains(ROLE_SUPERADMIN) || roles.contains(ROLE_ADMIN) || roles.contains(ROLE_AUDITOR)) {
            return ALL;
        }
        return SELF;
    }
}
