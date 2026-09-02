package io.github.sandbox.admin.bridge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对接 python-sandbox 的配置项（T-0027，design.md §6.3、§10.4）。
 *
 * <p>对应 application.yml：</p>
 * <pre>
 * admin:
 *   internal:
 *     token: ${ADMIN_INTERNAL_TOKEN:...}   # 内部共享凭证，ENV 可覆盖，不入库不进 Redis
 *   sandbox:
 *     base-url: ${SANDBOX_BASE_URL:...}    # python-sandbox 服务地址
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "admin")
public class SandboxBridgeProperties {

    /** 内部凭证配置 */
    private Internal internal = new Internal();

    /** python-sandbox 服务配置 */
    private Sandbox sandbox = new Sandbox();

    @Data
    public static class Internal {
        /** X-Admin-Internal-Token 值（默认决策 #9，独立于客户端 ApiKey 通道） */
        private String token;
    }

    @Data
    public static class Sandbox {
        /** python-sandbox 服务基址（如 http://python-sandbox:8080） */
        private String baseUrl = "http://localhost:8080";
    }
}
