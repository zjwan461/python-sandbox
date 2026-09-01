package io.github.sandbox.admin.common.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 路由拦截与 CORS 配置（T-0014/T-0016，design.md §4.2、§4.6）。
 *
 * <p>拦截口径：{@code /admin-api/**}（应用内即 {@code /**}，context-path 已设为 /admin-api）
 * 默认全部要求登录；白名单：登录、验证码。</p>
 *
 * <p>踢下线策略由 application.yml 中 {@code sa-token.is-concurrent=false} 承载：
 * 同一账号新会话建立后旧 token 被顶替失效，旧端下次访问得到 {@code BE_REPLACED}
 * 并由全局异常器翻译为 20004（被踢下线语义）。</p>
 */
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    /** 登录白名单（相对 context-path /admin-api） */
    public static final String[] AUTH_WHITELIST = {
            "/auth/login",
            "/auth/captcha",
            "/error"
    };

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册 Sa-Token 拦截器：开启注解鉴权（@SaCheckPermission/@SaCheckRole）+ 路由级登录校验
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(AUTH_WHITELIST);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 统一 CORS：开放 admin-web 来源（design.md §4.6，不在各 Controller 单独处理）
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
