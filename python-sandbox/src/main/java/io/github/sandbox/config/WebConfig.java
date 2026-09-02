package io.github.sandbox.config;

import io.github.sandbox.interceptor.ApiKeyAuthInterceptor;
import io.github.sandbox.interceptor.InternalTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 拦截器链装配（T-0023/T-0026，design.md §8.1）。
 *
 * <p>链顺序：TraceFilter（Servlet Filter，Order=1，生成/透传 traceId）
 * → ApiKeyAuthInterceptor（ApiKey 校验 + 限流判定，作用于 /api/sandbox/**）
 * → InternalTokenInterceptor（内部凭证校验，独立通道，作用于 /internal/**）
 * → Controller → Aspect（日志落库）。</p>
 *
 * <p>既有基于静态单 key 的 apiKeyInterceptor 已替换为基于 client_api_key 查表校验；
 * 内部 /internal/** 不进入 ApiKey 鉴权通道，两者凭证完全分离（默认决策 #9）。</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ApiKeyAuthInterceptor apiKeyAuthInterceptor;
    private final InternalTokenInterceptor internalTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // [2]+[3] ApiKey 校验与限流：作用于全部 /api/sandbox/** 业务通道（T-0023 验收口径），
        // 内部 /internal/** 与健康检查、actuator 不进入该鉴权通道。
        registry.addInterceptor(apiKeyAuthInterceptor)
                .addPathPatterns("/api/sandbox/**")
                .order(1);

        // 内部接口独立凭证通道：不占用 /api/sandbox/**
        registry.addInterceptor(internalTokenInterceptor)
                .addPathPatterns("/internal/**")
                .order(2);
    }
}
