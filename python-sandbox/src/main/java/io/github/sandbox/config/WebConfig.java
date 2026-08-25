package io.github.sandbox.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SandboxConfig sandboxConfig;

    @Bean
    public HandlerInterceptor apiKeyInterceptor() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                String uri = request.getRequestURI();
                if ("/health".equals(uri)) {
                    return true;
                }
                
                String apiKey = request.getHeader("X-Api-Key");
                if (apiKey == null || !apiKey.equals(sandboxConfig.getApiKey())) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    try {
                        response.getWriter().write("{\"error\":\"Unauthorized: Invalid or missing API key\"}");
                    } catch (Exception e) {
                        // ignore
                    }
                    return false;
                }
                return true;
            }
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiKeyInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/health");
    }
}
