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

    /**
     * @deprecated 已被 client_api_key 查表认证取代（T-0023）。仅当 ratelimit.legacy-static-key-enabled=true
     *             时作为过渡兼容通道保留；默认关闭。
     */
    @Deprecated
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
     * 代码危险检测（CodeGuard）配置。
     * 策略开关（静态校验/模型推理/失败降级）以数据库 sys_config 为准（管理端可改）；
     * 本节提供推理服务地址、超时与 sys_config 缺键时的本地回落值。
     */
    private CodeGuard codeGuard = new CodeGuard();

    /**
     * Python 代码安全校验的嵌套配置项。
     * 默认拦截：shutil/subprocess/ctypes 等危险模块导入，
     * 以及 os.system、subprocess.run、shutil.rmtree、eval、exec、__import__ 等危险调用。
     */
    // ==================== 限流与鉴权改造（批次4） ====================

    /** 限流相关配置 */
    private Ratelimit ratelimit = new Ratelimit();

    /** 内部接口（/internal/**）配置 */
    private Internal internal = new Internal();

    @Data
    public static class Ratelimit {
        /** 定时拉取限流规则间隔（毫秒），默认 60s（design.md §7.4） */
        private long refreshIntervalMillis = 60000L;

        /** 限流计数器清理间隔（毫秒），默认 10min */
        private long cleanupIntervalMillis = 600000L;

        /**
         * sys_config 缺失 ratelimit.anonymous.allowed 键时的本地回落值（默认决策 #10：默认严格 false）。
         * 正常运行时以数据库 sys_config 为准。
         */
        private boolean anonymousAllowedFallback = false;
    }

    @Data
    public static class Internal {
        /**
         * 管理端内部共享密钥（design.md §6.3 默认决策 #9）。
         * 经 ENV ADMIN_INTERNAL_TOKEN 覆盖；不写入数据库。空值时拒绝一切内部调用。
         */
        private String token = "";
    }

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

    @Data
    public static class CodeGuard {
        /** 模型推理检测服务地址（train/infer 的 api_server / vllm_api_server），如 http://code-detect:8000 */
        private String detectBaseUrl = "http://localhost:8000";

        /** 调用推理服务的连接与读取超时（毫秒），默认 5s */
        private int detectTimeoutMillis = 5000;

        /** sys_config 缺失 codeguard.static.enabled 键时的本地回落值（默认 true） */
        private boolean staticEnabledFallback = true;

        /** sys_config 缺失 codeguard.model.enabled 键时的本地回落值（默认 false，需显式开启） */
        private boolean modelEnabledFallback = false;

        /** sys_config 缺失 codeguard.model.fail-open 键时的本地回落值（默认 true：推理服务不可用时放行） */
        private boolean modelFailOpenFallback = true;
    }
}
