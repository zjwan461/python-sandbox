package io.github.sandbox.admin.common.datapermission;

/**
 * 数据权限忽略上下文（T-0021，供批次3 复用）。
 *
 * <p>某些内部查询（如登录时按用户名查账号、跨归属统计）必须绕过 SELF 行过滤，
 * 使用 {@link #runIgnored} 包裹即可在作用域内禁用数据权限拦截器；
 * 作用域结束自动恢复，避免 ThreadLocal 泄漏。</p>
 *
 * <pre>{@code
 * // 用法示例：
 * List<ClientApp> all = DataPermissionIgnoreHolder.runIgnored(() -> mapper.selectList(...));
 * }</pre>
 */
public final class DataPermissionIgnoreHolder {

    private static final ThreadLocal<Boolean> IGNORED = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private DataPermissionIgnoreHolder() {
    }

    /** 当前线程是否处于"忽略数据权限"作用域 */
    public static boolean isIgnored() {
        return Boolean.TRUE.equals(IGNORED.get());
    }

    /** 在忽略数据权限的作用域内执行（有返回值） */
    public static <T> T runIgnored(java.util.function.Supplier<T> supplier) {
        boolean previous = isIgnored();
        IGNORED.set(Boolean.TRUE);
        try {
            return supplier.get();
        } finally {
            restore(previous);
        }
    }

    /** 在忽略数据权限的作用域内执行（无返回值） */
    public static void runIgnored(Runnable runnable) {
        boolean previous = isIgnored();
        IGNORED.set(Boolean.TRUE);
        try {
            runnable.run();
        } finally {
            restore(previous);
        }
    }

    private static void restore(boolean previous) {
        if (previous) {
            IGNORED.set(Boolean.TRUE);
        } else {
            IGNORED.remove();
        }
    }
}
