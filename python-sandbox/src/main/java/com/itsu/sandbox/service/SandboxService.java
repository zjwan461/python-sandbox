package com.itsu.sandbox.service;

import com.itsu.sandbox.config.SandboxConfig;
import com.itsu.sandbox.exception.SandboxException;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@EnableScheduling
public class SandboxService {

    private final SandboxConfig config;
    private final Map<String, SandboxSession> sessions = new ConcurrentHashMap<>();

    public SandboxService(SandboxConfig config) {
        this.config = config;
    }

    /**
     * 创建新的沙箱会话和容器
     */
    public String createContainer(String sessionId) {
        if (sessions.containsKey(sessionId)) {
            throw new SandboxException("DUPLICATE_SESSION", "Session already exists: " + sessionId);
        }

        // 检查并处理最大容器数量限制
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

        Process createProcess = runCommand("docker", "create",
                "--name", containerName,
                "-e", "PYTHONUNBUFFERED=1", "-t", "-i", config.getImage());

        checkExitCode(createProcess, "Failed to create container");
        String containerId = readStdout(createProcess).trim();

        Process startProcess = runCommand("docker", "start", containerId);
        checkExitCode(startProcess, "Failed to start container");

        SandboxSession session = new SandboxSession(sessionId, containerId, containerName, Instant.now());
        sessions.put(sessionId, session);

        log.info("Created and started sandbox container: {} ({})", containerName,
                containerId.substring(0, Math.min(12, containerId.length())));
        return containerId;
    }

    public CommandResult execInContainer(String sessionId, String... command) {
        SandboxSession session = getSession(sessionId);
        Process process = runCommand("docker", "exec", session.containerId, "sh", "-c", String.join(" ", command));
        int exitCode = waitForOutput(process);
        return new CommandResult(exitCode, readStdout(process), readStderr(process));
    }

    public CommandResult runPythonCode(String sessionId, String code) {
        SandboxSession session = getSession(sessionId);
        String tmpFile = "/tmp/sandbox_" + System.currentTimeMillis() + ".py";

        // 使用 base64 编码避免管道和转义问题
        String encoded = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
        Process writeProcess = runCommand("docker", "exec", session.containerId,
                "sh", "-c", "echo \"" + encoded + "\" | base64 -d > " + tmpFile);
        checkExitCode(writeProcess, "Failed to write Python code");

        try {
            return execInContainer(sessionId, "python", tmpFile);
        } finally {
            try {
                execInContainer(sessionId, "sh", "-c", "rm -f " + tmpFile);
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
        // 使用 base64 编码避免管道和转义问题
        String encoded = Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8));
        Process writeProcess = runCommand("docker", "exec", session.containerId,
                "sh", "-c", "echo \"" + encoded + "\" | base64 -d > " + containerPath);
        checkExitCode(writeProcess, "Failed to write file");
    }

    public String readFile(String sessionId, String containerPath) {
        SandboxSession session = getSession(sessionId);
        Process catProcess = runCommand("docker", "exec", session.containerId, "cat", containerPath);
        checkExitCode(catProcess, "Failed to read file: " + containerPath);
        return readStdout(catProcess);
    }

    public byte[] downloadFile(String sessionId, String containerPath) {
        SandboxSession session = getSession(sessionId);
        Process cpProcess = runCommand("docker", "cp", session.containerId + ":" + containerPath, "/dev/stdout");
        checkExitCode(cpProcess, "Failed to copy file from container");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream is = cpProcess.getInputStream()) {
            is.transferTo(baos);
        } catch (IOException e) {
            throw new SandboxException("FILE_READ_ERROR", "Failed to read file: " + e.getMessage(), e);
        }
        return baos.toByteArray();
    }

    public void uploadFile(String sessionId, String containerPath, byte[] content) {
        SandboxSession session = getSession(sessionId);
        String tmpHostFile = "/tmp/sandbox_upload_" + System.currentTimeMillis();
        try (FileOutputStream fos = new FileOutputStream(tmpHostFile)) {
            fos.write(content);
        } catch (IOException e) {
            throw new SandboxException("FILE_WRITE_ERROR", "Failed to save uploaded file: " + e.getMessage(), e);
        }
        Process cpProcess = runCommand("docker", "cp", tmpHostFile, session.containerId + ":" + containerPath);
        checkExitCode(cpProcess, "Failed to upload file");
        new File(tmpHostFile).delete();
    }

    public void removeContainer(String sessionId) {
        SandboxSession session = sessions.remove(sessionId);
        if (session == null)
            return;
        try {
            runCommand("docker", "kill", session.containerId).waitFor();
        } catch (Exception e) {
            log.warn("Kill failed: {}", e.getMessage());
        }
        try {
            runCommand("docker", "rm", "-f", session.containerId).waitFor();
            log.info("Removed container: {}", session.name);
        } catch (Exception e) {
            log.error("Remove failed: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void stopAndRemoveAllContainers() {
        log.info("Cleaning up all sandbox containers...");

        // 首先停止所有 python-sandbox_* 前缀的 Docker 容器
        try {
            Process ps = runCommand("docker", "ps", "-q", "--filter", "name=python-sandbox-");
            String containerIds = readStdout(ps).trim();
            if (!containerIds.isEmpty()) {
                for (String containerId : containerIds.split("\n")) {
                    containerId = containerId.trim();
                    if (!containerId.isEmpty()) {
                        log.info("Stopping container: {}", containerId);
                        try {
                            runCommand("docker", "stop", containerId).waitFor();
                        } catch (Exception e) {
                            log.warn("Failed to stop container {}: {}", containerId, e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to stop docker containers: {}", e.getMessage());
        }

        // 同时清理本服务进程创建的会话
        sessions.keySet().forEach(this::removeContainer);
        sessions.clear();
    }

    /**
     * 定期检查并清理超时会话（默认每小时执行）
     */
    @Scheduled(fixedRateString = "${sandbox.session-cleanup-interval-millis:3600000}")
    public void cleanUpExpiredSessions() {
        Instant now = Instant.now();
        long timeoutMillis = config.getSessionTimeoutMillis();
        sessions.entrySet().removeIf(entry -> {
            SandboxSession session = entry.getValue();
            Instant expireTime = session.getLastActivity().plusMillis(timeoutMillis);
            if (now.isAfter(expireTime)) {
                log.info("Removing expired session: {} (timeout: {}ms)", entry.getKey(), timeoutMillis);
                try {
                    runCommand("docker", "kill", session.containerId).waitFor();
                    runCommand("docker", "rm", "-f", session.containerId).waitFor();
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
            Process ps = runCommand("docker", "inspect", "-f", "{{.State.Running}}", session.containerId);
            return "true".equals(readStdout(ps).trim());
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

    private Process runCommand(String... command) {
        try {
            return new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new SandboxException("COMMAND_FAILED", "Failed to execute command: " + e.getMessage(), e);
        }
    }

    private void checkExitCode(Process process, String errorMsg) {
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new SandboxException("DOCKER_ERROR", errorMsg + ": " + readStderr(process), null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxException("INTERRUPTED", "Command interrupted", e);
        }
    }

    private int waitForOutput(Process process) {
        try {
            return process.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SandboxException("INTERRUPTED", "Command interrupted", e);
        }
    }

    private String readStdout(Process process) {
        try (InputStream is = process.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private String readStderr(Process process) {
        try (InputStream is = process.getErrorStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private void cleanupContainer(String name) {
        try {
            Process ps = runCommand("docker", "ps", "-a", "--filter", "name=" + name, "--format", "{{.ID}}");
            String output = readStdout(ps).trim();
            if (!output.isEmpty()) {
                runCommand("docker", "kill", output).waitFor();
                runCommand("docker", "rm", "-f", output).waitFor();
                log.info("Cleaned up existing container: {}", name);
            }
        } catch (Exception e) {
            log.warn("Error cleaning up container {}: {}", name, e.getMessage());
        }
    }

    /**
     * 如果超过最大容器数量，删除最早创建的会话并清理其容器
     */
    private void evictOldestSessionIfNecessary() {
        if (sessions.size() < config.getMaxContainers()) {
            return; // 未达到限制，无需清理
        }

        // 找到最早创建的会话（通过 lastActivity 判断）
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
}
