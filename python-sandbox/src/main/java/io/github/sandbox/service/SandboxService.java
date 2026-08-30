package io.github.sandbox.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;

import io.github.sandbox.config.SandboxConfig;
import io.github.sandbox.exception.SandboxException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@EnableScheduling
@Getter
public class SandboxService {

    private final SandboxConfig config;
    private final PythonCodeValidator pythonCodeValidator;
    private final Map<String, SandboxSession> sessions = new ConcurrentHashMap<>();
    private DockerClient dockerClient;
    public static final String DEFAULT_SESSION_ID = "default";

    public SandboxService(SandboxConfig config, PythonCodeValidator pythonCodeValidator) {
        this.config = config;
        this.pythonCodeValidator = pythonCodeValidator;
    }

    @PostConstruct
    public void init() {
        dockerClient = createDockerClient();
        if (config.isPullImageOnStartup()) {
            pullImageOnStartup();
        } else {
            log.info("pull-image-on-startup is disabled, skip pre-pulling image: {}", config.getImage());
        }
        if (config.isCreateDefaultContainerOnStartup()) {
            createContainer(DEFAULT_SESSION_ID);
        } else {
            log.info("create-default-container-on-startup is disabled, skill create default container: {}",
                    DEFAULT_SESSION_ID);
        }
    }

    @PreDestroy
    public void destroy() {
        stopAndRemoveAllContainers();
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (IOException e) {
                log.warn("Failed to close docker client: {}", e.getMessage());
            }
        }
    }

    private DockerClient createDockerClient() {
        try {
            DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();

            if (StringUtils.hasText(config.getDockerHost())) {
                configBuilder.withDockerHost(config.getDockerHost());
            }
            if (StringUtils.hasText(config.getDockerCertPath())) {
                configBuilder.withDockerCertPath(config.getDockerCertPath());
            }
            if (StringUtils.hasText(config.getDockerApiVersion())) {
                configBuilder.withApiVersion(config.getDockerApiVersion());
            }
            configBuilder.withDockerTlsVerify(config.isDockerTlsVerify());

            DockerClientConfig dockerClientConfig = configBuilder.build();

            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(dockerClientConfig.getDockerHost())
                    .sslConfig(dockerClientConfig.getSSLConfig())
                    .build();

            DockerClient client = DockerClientImpl.getInstance(dockerClientConfig, httpClient);
            client.pingCmd().exec();
            log.info("Docker client initialized successfully, host: {}", dockerClientConfig.getDockerHost());
            return client;
        } catch (Exception e) {
            throw new SandboxException("DOCKER_CONNECT_ERROR",
                    "Failed to connect to Docker daemon: " + e.getMessage(), e);
        }
    }

    // ==================== 会话管理 ====================

    public String createContainer(String sessionId) {
        if (sessions.containsKey(sessionId)) {
            throw new SandboxException("DUPLICATE_SESSION", "Session already exists: " + sessionId);
        }

        String behavior = config.getMaxContainersBehavior();
        if ("evict-oldest".equals(behavior)) {
            evictOldestSessionIfNecessary();
        }

        int currentCount = sessions.size();
        int maxContainers = config.getMaxContainers();
        if (currentCount >= maxContainers) {
            throw new SandboxException("MAX_CONTAINERS_REACHED",
                    "Maximum container limit reached (" + maxContainers + "). Cannot create new session: " + sessionId);
        }

        log.info("Creating container {} ({}/{})", sessionId, currentCount + 1, maxContainers);

        String containerName = config.getContainerNamePrefix() + sessionId;
        cleanupContainer(containerName);

        try {
            // 构建容器创建命令
            var createCmd = dockerClient.createContainerCmd(config.getImage())
                    .withName(containerName)
                    .withEnv("PYTHONUNBUFFERED=1")
                    .withTty(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true);

            // 设置容器内存限制（containerMemoryLimit > 0 时生效）
            long memoryLimit = config.getContainerMemoryLimit();
            if (memoryLimit > 0) {
                createCmd.withMemory(memoryLimit);
                // 同时设置 swap 为 memory 的 2 倍，避免 swap 过大
                createCmd.withMemorySwap(memoryLimit * 2);
                log.info("Container memory limit: {} bytes ({} MB)", memoryLimit, memoryLimit / (1024 * 1024));
            } else {
                log.info("Container memory limit: unlimited");
            }

            CreateContainerResponse response = createCmd.exec();
            String containerId = response.getId();
            dockerClient.startContainerCmd(containerId).exec();

            SandboxSession session = new SandboxSession(sessionId, containerId, containerName, Instant.now());
            sessions.put(sessionId, session);

            log.info("Created and started sandbox container: {} ({})", containerName,
                    containerId.substring(0, Math.min(12, containerId.length())));
            return containerId;
        } catch (DockerException e) {
            throw new SandboxException("DOCKER_ERROR", "Failed to create container: " + e.getLocalizedMessage(), e);
        }
    }

    public CommandResult execInContainer(String sessionId, String... command) {
        SandboxSession session = getSession(sessionId);
        try {
            ExecCreateCmdResponse execCreate = dockerClient.execCreateCmd(session.containerId)
                    .withCmd(command)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .exec();

            String execId = execCreate.getId();

            ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();

            ResultCallback.Adapter<Frame> callback = dockerClient.execStartCmd(execId)
                    .exec(new ExecOutputCallback(stdoutStream, stderrStream));
            callback.awaitCompletion(300, TimeUnit.SECONDS);

            int exitCode = 0;
            try {
                InspectExecResponse inspectResponse = dockerClient.inspectExecCmd(execId).exec();
                if (inspectResponse.getExitCodeLong() != null) {
                    exitCode = inspectResponse.getExitCodeLong().intValue();
                }
            } catch (Exception e) {
                log.warn("Failed to get exit code: {}", e.getMessage());
            }

            return new CommandResult(exitCode,
                    stdoutStream.toString(StandardCharsets.UTF_8),
                    stderrStream.toString(StandardCharsets.UTF_8));
        } catch (DockerException e) {
            throw new SandboxException("DOCKER_ERROR", "Failed to exec command: " + e.getLocalizedMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxException("INTERRUPTED", "Command interrupted: " + e.getLocalizedMessage(), e);
        }
    }

    public CommandResult runPythonCode(String sessionId, String code) {
        SandboxSession session = getSession(sessionId);

        // 在写入容器前先做静态安全校验，避免危险代码进入沙箱
        pythonCodeValidator.validate(code);

        String tmpFile = "/tmp/sandbox_" + System.currentTimeMillis() + ".py";

        writeFile(sessionId, tmpFile, code);

        try {
            return execInContainer(sessionId, "python", tmpFile);
        } finally {
            try {
                execInContainer(sessionId, "rm", "-f", tmpFile);
            } catch (Exception e) {
                log.warn("Cleanup failed: {}", e.getMessage());
            }
        }
    }

    public CommandResult pipInstall(String sessionId, String packageName) {
        return execInContainer(sessionId, "pip", "install", packageName);
    }

    public CommandResult pipUninstall(String sessionId, String packageName) {
        return execInContainer(sessionId, "pip", "uninstall", "-y", packageName);
    }

    public CommandResult pipList(String sessionId) {
        return execInContainer(sessionId, "pip", "list", "--format=freeze");
    }

    public void writeFile(String sessionId, String containerPath, String content) {
        SandboxSession session = getSession(sessionId);
        try {
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            String tmpFile = writeToTempFile(contentBytes);
            String dir = extractDirectoryPath(containerPath);
            String fileName = extractFileName(containerPath);
            String tmpFileName = extractFileName(tmpFile);

            dockerClient.copyArchiveToContainerCmd(session.containerId)
                    .withHostResource(tmpFile)
                    .withRemotePath(dir)
                    .exec();

            new File(tmpFile).delete();

            if (!tmpFileName.equals(fileName)) {
                execInContainer(sessionId, "sh", "-c",
                        "mv '" + dir + "/" + tmpFileName + "' '" + containerPath + "'");
            }
        } catch (IOException e) {
            throw new SandboxException("FILE_WRITE_ERROR", "Failed to write file: " + e.getMessage(), e);
        } catch (DockerException e) {
            throw new SandboxException("DOCKER_ERROR", "Failed to write file: " + e.getLocalizedMessage(), e);
        }
    }

    public String readFile(String sessionId, String containerPath) {
        byte[] content = downloadFile(sessionId, containerPath);
        return new String(content, StandardCharsets.UTF_8);
    }

    public byte[] downloadFile(String sessionId, String containerPath) {
        SandboxSession session = getSession(sessionId);
        try {
            try (InputStream is = dockerClient.copyArchiveFromContainerCmd(session.containerId, containerPath).exec()) {
                return readTarEntry(is);
            }
        } catch (DockerException e) {
            throw new SandboxException("DOCKER_ERROR",
                    "Failed to read file: " + e.getLocalizedMessage(), e);
        } catch (IOException e) {
            throw new SandboxException("FILE_READ_ERROR", "Failed to read file: " + e.getMessage(), e);
        }
    }

    public String uploadFile(String sessionId, String containerPath, byte[] content, String originalFileName) {
        SandboxSession session = getSession(sessionId);

        String actualContainerPath;
        if (containerPath.endsWith("/")) {
            actualContainerPath = containerPath + originalFileName;
        } else if (containerPath.contains(".")) {
            actualContainerPath = containerPath;
        } else {
            actualContainerPath = containerPath + "/" + originalFileName;
        }

        try {
            String dir = extractDirectoryPath(actualContainerPath);
            String tmpFile = writeToTempFile(content);

            dockerClient.copyArchiveToContainerCmd(session.containerId)
                    .withHostResource(tmpFile)
                    .withRemotePath(dir)
                    .exec();

            new File(tmpFile).delete();

            String fileName = extractFileName(actualContainerPath);
            String tmpFileName = extractFileName(tmpFile);
            if (!tmpFileName.equals(fileName)) {
                execInContainer(sessionId, "sh", "-c",
                        "mv '" + dir + "/" + tmpFileName + "' '" + actualContainerPath + "'");
            }
            return actualContainerPath;
        } catch (IOException e) {
            throw new SandboxException("FILE_WRITE_ERROR", "Failed to upload file: " + e.getMessage(), e);
        } catch (DockerException e) {
            throw new SandboxException("DOCKER_ERROR", "Failed to upload file: " + e.getLocalizedMessage(), e);
        }
    }

    public void removeContainer(String sessionId) {
        SandboxSession session = sessions.remove(sessionId);
        if (session == null)
            return;
        try {
            dockerClient.killContainerCmd(session.containerId).exec();
        } catch (Exception e) {
            log.warn("Kill failed: {}", e.getMessage());
        }
        try {
            dockerClient.removeContainerCmd(session.containerId).withForce(true).exec();
            log.info("Removed container: {}", session.name);
        } catch (Exception e) {
            log.error("Remove failed: {}", e.getMessage());
        }
    }

    private void pullImageOnStartup() {
        String image = config.getImage();
        log.info("Pre-pulling sandbox image on startup: {}", image);
        try {
            dockerClient.pullImageCmd(image)
                    .exec(new ResultCallback.Adapter<PullResponseItem>() {
                    })
                    .awaitCompletion();
            log.info("Successfully pre-pulled image: {}", image);
        } catch (Exception e) {
            log.error("Exception while pre-pulling image {}: {}", image, e.getMessage(), e);
        }
    }

    public void stopAndRemoveAllContainers() {
        log.info("Cleaning up all sandbox containers...");
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withNameFilter(List.of(config.getContainerNamePrefix()))
                    .exec();
            for (Container container : containers) {
                try {
                    dockerClient.stopContainerCmd(container.getId()).withTimeout(5).exec();
                } catch (Exception e) {
                    log.warn("Failed to stop container {}: {}", container.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to list containers: {}", e.getMessage());
        }
        sessions.keySet().forEach(this::removeContainer);
        sessions.clear();
    }

    @Scheduled(fixedRateString = "${sandbox.session-cleanup-interval-millis:3600000}")
    public void cleanUpExpiredSessions() {
        Instant now = Instant.now();
        long timeoutMillis = config.getSessionTimeoutMillis();
        sessions.entrySet().removeIf(entry -> {
            SandboxSession session = entry.getValue();
            Instant expireTime = session.getLastActivity().plusMillis(timeoutMillis);
            if (now.isAfter(expireTime) && !session.getSessionId().equals(DEFAULT_SESSION_ID)) {
                log.info("Removing expired session: {} (timeout: {}ms)", entry.getKey(), timeoutMillis);
                try {
                    dockerClient.killContainerCmd(session.containerId).exec();
                    dockerClient.removeContainerCmd(session.containerId).withForce(true).exec();
                    log.info("Cleaned up expired container: {}", session.name);
                } catch (Exception e) {
                    log.error("Failed to cleanup expired container {}: {}", session.name, e.getMessage());
                }
                return true;
            }
            return false;
        });
    }

    public boolean isActive(String sessionId) {
        SandboxSession session = sessions.get(sessionId);
        if (session == null)
            return false;
        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(session.containerId).exec();
            return Boolean.TRUE.equals(inspect.getState().getRunning());
        } catch (Exception e) {
            sessions.remove(sessionId);
            return false;
        }
    }

    public int getActiveCount() {
        return sessions.size();
    }

    public String generateSessionId() {
        return "session-" + System.currentTimeMillis() + "-" + Long.toString((long) (Math.random() * 1000000), 36);
    }

    // ==================== Private helpers ====================

    private SandboxSession getSession(String sessionId) {
        SandboxSession session = sessions.get(sessionId);
        if (session == null) {
            throw new SandboxException("SESSION_NOT_FOUND", "Invalid or expired session: " + sessionId);
        }
        if (!isActive(sessionId)) {
            sessions.remove(sessionId);
            throw new SandboxException("SESSION_EXPIRED", "Session container no longer running: " + sessionId);
        }
        session.setLastActivity(Instant.now());
        return session;
    }

    private void cleanupContainer(String name) {
        try {
            List<Container> containers = dockerClient.listContainersCmd()
                    .withShowAll(true)
                    .withNameFilter(List.of("/" + name))
                    .exec();
            for (Container container : containers) {
                try {
                    dockerClient.killContainerCmd(container.getId()).exec();
                } catch (Exception ignored) {
                }
                try {
                    dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
                    log.info("Cleaned up existing container: {}", name);
                } catch (Exception e) {
                    log.warn("Error cleaning up container {}: {}", name, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Error cleaning up container {}: {}", name, e.getMessage());
        }
    }

    private void evictOldestSessionIfNecessary() {
        if (sessions.size() < config.getMaxContainers())
            return;
        String oldestSessionId = null;
        Instant oldestActivity = null;
        for (Map.Entry<String, SandboxSession> entry : sessions.entrySet()) {
            Instant activityTime = entry.getValue().getLastActivity();
            if (oldestActivity == null || activityTime.isBefore(oldestActivity)) {
                oldestActivity = activityTime;
                oldestSessionId = entry.getKey();
            }
        }
        if (oldestSessionId != null) {
            log.info("Evicting oldest session: {} (last active: {})", oldestSessionId, oldestActivity);
            removeContainer(oldestSessionId);
        }
    }

    private String writeToTempFile(byte[] content) throws IOException {
        File tmp = File.createTempFile("sandbox_upload_", ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmp)) {
            fos.write(content);
        }
        return tmp.getAbsolutePath();
    }

    private String extractDirectoryPath(String path) {
        int lastSlash = path.lastIndexOf('/');
        return lastSlash > 0 ? path.substring(0, lastSlash) : "/";
    }

    private String extractFileName(String path) {
        int lastSlash = path.lastIndexOf('/');
        int lastBackslash = path.lastIndexOf('\\');
        int lastSep = Math.max(lastSlash, lastBackslash);
        return lastSep >= 0 ? path.substring(lastSep + 1) : path;
    }

    private byte[] readTarEntry(InputStream tarStream) throws IOException {
        byte[] header = new byte[512];
        int headerRead = tarStream.read(header);
        if (headerRead < 512) {
            throw new IOException("Invalid tar stream");
        }
        String sizeStr = new String(header, 124, 12, StandardCharsets.UTF_8).trim();
        long size = Long.parseLong(sizeStr, 8);
        byte[] content = new byte[(int) size];
        int offset = 0;
        while (offset < size) {
            int read = tarStream.read(content, offset, (int) size - offset);
            if (read < 0)
                break;
            offset += read;
        }
        return content;
    }

    // ==================== Data classes ====================

    public static class CommandResult {
        private final int exitCode;
        private final String stdout;
        private final String stderr;

        public CommandResult(int exitCode, String stdout, String stderr) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
        }

        public int getExitCode() {
            return exitCode;
        }

        public String getStdout() {
            return stdout;
        }

        public String getStderr() {
            return stderr;
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }

        public String getCombinedOutput() {
            return stdout + (stderr.isEmpty() ? "" : "\n" + stderr);
        }
    }

    @Data
    private static class SandboxSession {
        final String sessionId;
        final String containerId;
        final String name;
        private Instant lastActivity;

        SandboxSession(String sessionId, String containerId, String name, Instant lastActivity) {
            this.sessionId = sessionId;
            this.containerId = containerId;
            this.name = name;
            this.lastActivity = lastActivity;
        }
    }

    // ==================== Docker exec output callback ====================

    private static class ExecOutputCallback extends ResultCallback.Adapter<Frame> {
        private final OutputStream stdout;
        private final OutputStream stderr;

        public ExecOutputCallback(OutputStream stdout, OutputStream stderr) {
            this.stdout = stdout;
            this.stderr = stderr;
        }

        @Override
        public void onNext(Frame frame) {
            try {
                StreamType streamType = frame.getStreamType();
                if (streamType == StreamType.STDOUT) {
                    stdout.write(frame.getPayload());
                } else if (streamType == StreamType.STDERR) {
                    stderr.write(frame.getPayload());
                }
            } catch (IOException e) {
                log.warn("Failed to write exec output: {}", e.getMessage());
            }
        }
    }
}
