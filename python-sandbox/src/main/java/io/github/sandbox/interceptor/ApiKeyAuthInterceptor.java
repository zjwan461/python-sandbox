package io.github.sandbox.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sandbox.context.AuthContext;
import io.github.sandbox.entity.ApiLog;
import io.github.sandbox.service.ApiKeyAuthService;
import io.github.sandbox.service.AsyncLogService;
import io.github.sandbox.service.RatelimitService;
import io.github.sandbox.util.TraceUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ApiKey 鉴权 + 限流一体化拦截器（T-0023/T-0024，design.md §8.1~§8.3）。
 *
 * <p>作用于 {@code /api/sandbox/**}（内部 {@code /internal/**} 与 {@code /health}
 * 不进入本通道）。链顺序：TraceFilter(Filter, Order=1) → 本拦截器
 * [2]ApiKey 校验 → [3]限流判定 → Controller。</p>
 *
 * <p>ApiKey 校验通过后，把 clientId/apiKeyId/ownerUserId 写入 {@link AuthContext}
 * 线程上下文，供 ApiLogAspect / SandboxOperationLogAspect 填充日志归属字段，
 * 并供会话创建登记归属。限流命中写 {@code api_log.rate_limit_hit=1 +
 * rate_limit_rule_id} 并返回 HTTP 429（含 Retry-After）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    private final ApiKeyAuthService apiKeyAuthService;
    private final RatelimitService ratelimitService;
    private final AsyncLogService asyncLogService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String plainKey = request.getHeader(ApiKeyAuthService.API_KEY_HEADER);

        // ---- [2] ApiKey 校验（缺失/未知/停用/撤销/过期/未生效/客户端停用/用户停用，30001~30005）----
        ApiKeyAuthService.AuthResult auth = apiKeyAuthService.authenticate(plainKey);
        if (!auth.isSuccess()) {
            if (auth.getCode() == ApiKeyAuthService.AuthResult.API_KEY_MISSING
                    && ratelimitService.isAnonymousAllowed()) {
                // 匿名灰度开启（sys_config ratelimit.anonymous.allowed=true）：放行但应用全局默认限流
                auth = apiKeyAuthService.anonymousPass();
            } else {
                AuthContext.clear();
                writeError(response, 401, auth.getErrorCode(), auth.getMessage(), auth.getCode());
                return false;
            }
        }
        AuthContext.Principal principal = auth.getPrincipal();
        AuthContext.setPrincipal(principal);

        // ---- [3] 限流判定（白名单 → API_KEY 滑动窗口 → CLIENT 令牌桶 → GLOBAL 默认）----
        RatelimitService.Hit hit;
        try {
            hit = ratelimitService.check(principal);
        } catch (Exception e) {
            // 判定器自身异常不应阻断业务（fail-open）
            log.error("限流判定异常，放行本次请求: {}", e.getMessage(), e);
            hit = null;
        }
        if (hit != null) {
            AuthContext.setRateLimitHit(AuthContext.RateLimitHit.builder()
                    .ruleId(hit.getRuleId())
                    .retryAfterSeconds(hit.retryAfterSeconds())
                    .build());
            response.setHeader("Retry-After", String.valueOf(hit.retryAfterSeconds()));
            if (ratelimitService.isStaleCache()) {
                // 规则拉取失败保留旧缓存时透出"配置未最新"业务语义（design.md §7.4）
                response.setHeader("X-Ratelimit-Config-Stale", "true");
            }
            // 被限流拒绝的请求不经过 Controller，ApiLogAspect 不会触发，此处直接补记 api_log
            writeRateLimitApiLog(request, principal, hit);
            AuthContext.clear();
            writeError(response, 429, "RATE_LIMIT_EXCEEDED", hit.message(), 30006);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 请求结束统一清理线程上下文（与 TraceFilter 的 traceId 清理双保险，避免线程池串号）
        AuthContext.clear();
    }

    /**
     * 限流命中直接落 api_log（design.md §7.5：response_code=429、rate_limit_hit=1、
     * rate_limit_rule_id 关联规则主键；命中全局默认阈值无规则行时 rule_id 为 NULL）。
     */
    private void writeRateLimitApiLog(HttpServletRequest request, AuthContext.Principal principal,
                                      RatelimitService.Hit hit) {
        try {
            ApiLog apiLog = new ApiLog();
            apiLog.setTraceId(TraceUtil.getTraceId());
            apiLog.setApiPath(request.getRequestURI());
            apiLog.setHttpMethod(request.getMethod());
            apiLog.setClientIp(TraceUtil.getClientIp(request));
            apiLog.setResponseCode(429);
            apiLog.setExecutionTime(0L);
            apiLog.setCreatedAt(LocalDateTime.now());
            if (principal != null) {
                apiLog.setClientId(principal.getClientId());
                apiLog.setApiKeyId(principal.getApiKeyId());
                apiLog.setOwnerUserId(principal.getOwnerUserId());
            }
            apiLog.setRateLimitHit(1);
            apiLog.setRateLimitRuleId(hit.getRuleId());
            asyncLogService.logApiAsync(apiLog);
        } catch (Exception e) {
            log.warn("限流命中 api_log 写入失败: {}", e.getMessage());
        }
    }

    private void writeError(HttpServletResponse response, int httpStatus, String error,
                            String message, int code) throws Exception {
        response.setStatus(httpStatus);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("code", code);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
