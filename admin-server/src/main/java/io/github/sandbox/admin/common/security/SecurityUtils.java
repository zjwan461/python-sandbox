package io.github.sandbox.admin.common.security;

import cn.dev33.satoken.stp.StpUtil;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;

/**
 * 当前登录用户上下文工具（T-0014/T-0021）。
 *
 * <p>统一从 Sa-Token Session 读取 {@link AdminLoginUser} 快照，
 * 供权限解析、数据权限拦截器、审计与自动填充使用。</p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * 静默获取登录用户快照：未登录或会话异常时返回 null（不抛出）。
     * 供自动填充等可能在登录前执行的场景使用。
     */
    public static AdminLoginUser getLoginUserQuietly() {
        try {
            if (!StpUtil.isLogin()) {
                return null;
            }
            Object obj = StpUtil.getSession().get(AdminLoginUser.SESSION_KEY);
            return obj instanceof AdminLoginUser u ? u : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前登录用户；未登录抛业务异常（20001 未登录语义由全局异常器兜底）。
     */
    public static AdminLoginUser getLoginUser() {
        AdminLoginUser user = getLoginUserQuietly();
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN);
        }
        return user;
    }

    /** 当前登录用户 ID（未登录返回 null） */
    public static Long getUserIdOrNull() {
        AdminLoginUser user = getLoginUserQuietly();
        return user == null ? null : user.getUserId();
    }

    /** 当前登录用户 ID（必须登录） */
    public static Long getUserId() {
        return getLoginUser().getUserId();
    }

    /** 当前用户名（必须登录） */
    public static String getUsername() {
        return getLoginUser().getUsername();
    }

    /** 是否 ALL 数据可见域（未登录视为 SELF，最小权限原则） */
    public static boolean isAllScope() {
        AdminLoginUser user = getLoginUserQuietly();
        return user != null && user.isAllScope();
    }
}
