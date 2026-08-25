package com.itsu.sandbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "sandbox")
public class SandboxConfig {
    
    private String apiKey;
    private String image = "python:3.12-trixie";
    private String containerNamePrefix = "python-sandbox-";
    
    /** 会话超时时间（毫秒），默认24小时 */
    private long sessionTimeoutMillis = 86400000L;
    
    /** 会话清理检查间隔（毫秒），默认1小时 */
    private long sessionCleanupIntervalMillis = 3600000L;
    
    /** 最大活跃容器数量，默认10个 */
    private int maxContainers = 10;
    
    /**
     * 超过最大容器数量时的处理策略：
     * reject - 拒绝创建新容器（默认）
     * evict-oldest - 删除最早创建的容器，然后创建新容器
     */
    private String maxContainersBehavior = "reject";

    /**
     * 是否在应用启动时预拉取 Python 镜像（默认 false）
     * true  - 启动 Bean 时调用 docker pull 预热镜像，避免首次创建会话延迟
     * false - 不预拉取，延迟到首次创建会话时再拉取
     */
    private boolean pullImageOnStartup = false;

    // ==================== Docker 连接配置 ====================

    /**
     * Docker 守护进程连接地址。
     * 留空时自动检测（优先本地 socket，其次 DOCKER_HOST 环境变量）。
     * 示例：
     *   - 本地 socket: unix:///var/run/docker.sock
     *   - TCP 连接: tcp://192.168.1.100:2375
     *   - TLS 连接: tcp://192.168.1.100:2376
     */
    private String dockerHost = "unix:///var/run/docker.sock";

    /**
     * TLS 证书目录路径（启用 TLS 时必填）。
     * 目录下应包含 ca.pem、cert.pem、key.pem 三个文件。
     */
    private String dockerCertPath = "";

    /**
     * 是否启用 TLS 验证（默认 false）。
     * 当 dockerHost 使用 tcp:// 且端口为 2376 时建议开启。
     */
    private boolean dockerTlsVerify = false;

    /**
     * Docker API 版本号。留空使用默认版本。
     * 示例：1.43
     */
    private String dockerApiVersion = "";
}
