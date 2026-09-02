package io.github.sandbox.context;

import lombok.Builder;
import lombok.Data;

/**
 * 鉴权与限流上下文（T-0023，design.md §8.1/§8.2）。
 *
 * <p>由 {@code ApiKeyAuthInterceptor} 在校验通过后写入线程上下文，
 * 供 {@code RateLimitInterceptor}、{@code ApiLogAspect}、{@code SandboxOperationLogAspect}
 * 及会话登记读取；请求结束时必须清理，避免线程池串号。</p>
 *
 * <p>无鉴权上下文（内部接口、健康检查、匿名灰度放行）时各字段允许为 NULL，
 * 不破坏既有写库语义。</p>
 */
public final class AuthContext {

    /**
     * 鉴权上下文快照。
     */
    @Data
    @Builder
    public static class Principal {
        /** 归属客户端（client_app.id）；匿名调用为 null */
        private Long clientId;
        /** 调用 ApiKey（client_api_key.id）；匿名调用为 null */
        private Long apiKeyId;
        /** 归属用户（COALESCE(bound_user_id, client_app.owner_user_id)）；可为 null */
        private Long ownerUserId;
        /** 是否匿名放行（灰度开关开启且未携带 ApiKey） */
        private boolean anonymous;
        /** ApiKey 限流白名单标志（rate_limit_exempt=1 时跳过全部规则） */
        private boolean rateLimitExempt;
    }

    /**
     * 限流命中信息（由 RateLimitInterceptor 写入，供 api_log 落库）。
     */
    @Data
    @Builder
    public static class RateLimitHit {
        /** 命中的限流规则主键（ratelimit_rule.id） */
        private Long ruleId;
        /** 建议重试秒数（由窗口类型推导） */
        private long retryAfterSeconds;
    }

    private static final ThreadLocal<Principal> PRINCIPAL = new ThreadLocal<>();
    private static final ThreadLocal<RateLimitHit> RATE_LIMIT_HIT = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void setPrincipal(Principal principal) {
        PRINCIPAL.set(principal);
    }

    /** 获取当前鉴权主体；无上下文返回 null */
    public static Principal getPrincipal() {
        return PRINCIPAL.get();
    }

    public static void setRateLimitHit(RateLimitHit hit) {
        RATE_LIMIT_HIT.set(hit);
    }

    /** 获取限流命中信息；未命中返回 null */
    public static RateLimitHit getRateLimitHit() {
        return RATE_LIMIT_HIT.get();
    }

    /** 请求结束统一清理（TraceFilter finally / 拦截器 afterCompletion 双保险） */
    public static void clear() {
        PRINCIPAL.remove();
        RATE_LIMIT_HIT.remove();
    }
}
