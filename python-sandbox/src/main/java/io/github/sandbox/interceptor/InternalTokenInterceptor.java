package io.github.sandbox.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sandbox.config.SandboxConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内部接口凭证校验拦截器（T-0026，design.md §6.3 默认决策 #9）。
 *
 * <p>作用于 {@code /internal/**}：统一校验 Header {@code X-Admin-Internal-Token}，
 * 与配置键 {@code sandbox.internal.token}（ENV {@code ADMIN_INTERNAL_TOKEN} 覆盖）比对；
 * 不匹配或缺失直接 401。内部凭证与客户端 ApiKey 完全分离，
 * 内部链路不进入 ApiKey 鉴权通道。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalTokenInterceptor implements HandlerInterceptor {

    public static final String HEADER_INTERNAL_TOKEN = "X-Admin-Internal-Token";

    private final SandboxConfig sandboxConfig;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String configured = sandboxConfig.getInternal().getToken();
        String provided = request.getHeader(HEADER_INTERNAL_TOKEN);
        // 未配置内部凭证时拒绝一切内部调用（防止空值误放行）
        if (configured == null || configured.isBlank()
                || provided == null || !constantTimeEquals(configured, provided)) {
            log.warn("内部接口凭证校验失败: uri={} remote={}", request.getRequestURI(), request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "INTERNAL_UNAUTHORIZED");
            body.put("message", "Missing or invalid X-Admin-Internal-Token");
            body.put("code", 20001);
            response.getWriter().write(objectMapper.writeValueAsString(body));
            return false;
        }
        return true;
    }

    /** 定时安全比较，避免凭证逐字节时序侧信道 */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
