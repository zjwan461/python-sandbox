package io.github.sandbox.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

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
     * true - 启动 Bean 时调用 docker pull 预热镜像，避免首次创建会话延迟
     * false - 不预拉取，延迟到首次创建会话时再拉取
     */
    private boolean pullImageOnStartup = false;

    /**
     * 是否在应用启动时启动默认容器
     * true - 启动 Bean 时调用 docker create创建默认容器
     * false - 不预先启动默认容器
     */
    private boolean createDefaultContainerOnStartup = true;

    // ==================== 容器资源限制 ====================

    /**
     * 单个容器的最大内存限制（字节）。
     * 默认 512MB (536870912 字节)。
     * 设置为 0 或负数表示不限制。
     * 示例：
     * - 512MB = 536870912
     * - 1GB = 1073741824
     * - 256MB = 268435456
     */
    private long containerMemoryLimit = 536870912L;

    // ==================== Docker 连接配置 ====================

    /**
     * Docker 守护进程连接地址。
     * 留空时自动检测（优先本地 socket，其次 DOCKER_HOST 环境变量）。
     * 示例：
     * - 本地 socket: unix:///var/run/docker.sock
     * - TCP 连接: tcp://192.168.1.100:2375
     * - TLS 连接: tcp://192.168.1.100:2376
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

    // ==================== Python 代码安全校验 ====================

    /**
     * Python 代码安全校验配置。
     * 用于 runPythonCode 接口，在执行前对代码做静态分析，拦截危险模块与方法。
     */
    private PythonSecurity pythonSecurity = new PythonSecurity();

    /**
     * Python 代码安全校验的嵌套配置项。
     * 默认拦截：shutil/subprocess/ctypes 等危险模块导入，
     * 以及 os.system、subprocess.run、shutil.rmtree、eval、exec、__import__ 等危险调用。
     */
    @Data
    public static class PythonSecurity {
        /** 是否启用 Python 代码安全校验（默认 true） */
        private boolean enabled = true;

        /** Python 代码最大长度（字符数），默认 100KB，防止超大代码攻击 */
        private int maxCodeLength = 100 * 1024;

        /** 追加禁用的模块名（顶级模块名），与默认黑名单合并生效 */
        private List<String> extraBlockedModules = new ArrayList<>();

        /** 追加禁用的内置函数名（与默认黑名单合并） */
        private List<String> extraBlockedFunctions = new ArrayList<>();

        /** 追加禁用的方法调用，格式为 module.func（与默认黑名单合并） */
        private List<String> extraBlockedCalls = new ArrayList<>();
    }
}
