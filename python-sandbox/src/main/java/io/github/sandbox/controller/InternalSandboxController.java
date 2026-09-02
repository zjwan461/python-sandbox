package io.github.sandbox.controller;

import io.github.sandbox.service.RatelimitService;
import io.github.sandbox.service.SandboxService;
import io.github.sandbox.service.SandboxService.SessionSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端内部接口（T-0025/T-0026，design.md §8.4、§10.4）。
 *
 * <p>路径空间 {@code /internal/sandbox/**} 与公开 {@code /api/sandbox/**} 互不重叠；
 * 由 {@code InternalTokenInterceptor} 统一校验 {@code X-Admin-Internal-Token}，
 * 不进入客户端 ApiKey 鉴权通道。本控制器不暴露沙箱执行细节，
 * 仅提供活跃会话枚举/详情/强销与限流规则刷新。</p>
 *
 * <p>JSON 契约与 admin-server SandboxBridgeClient（批次3）对齐，不得单方面变更：</p>
 * <ul>
 *   <li>GET /internal/sandbox/sessions → {"sessions":[{sessionId,containerId,containerName,
 *       createTime,lastActiveTime("yyyy-MM-dd HH:mm:ss"),isDefault,ownerClientId,ownerApiKeyId,ownerUserId}]}</li>
 *   <li>GET /internal/sandbox/sessions/{id}/detail → {"session":{...}}；不存在 404</li>
 *   <li>DELETE /internal/sandbox/sessions/{id} → {"success":bool,"message":str,"remainingSessions":int}</li>
 *   <li>POST /internal/sandbox/ratelimit/reload → {"success":bool}</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/internal/sandbox")
@RequiredArgsConstructor
public class InternalSandboxController {

    /** 时间字符串口径：yyyy-MM-dd HH:mm:ss（admin-server 侧透传不再解析） */
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final SandboxService sandboxService;
    private final RatelimitService ratelimitService;

    /** 列出当前进程内活跃会话（内存快照；孤儿会话不伪造） */
    @GetMapping("/sessions")
    public Map<String, Object> listSessions() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sessions", sandboxService.listSessionSnapshots().stream()
                .map(InternalSandboxController::toVO).toList());
        return body;
    }

    /** 单会话详情；不存在返回 404 语义 */
    @GetMapping("/sessions/{sessionId}/detail")
    public ResponseEntity<Map<String, Object>> sessionDetail(@PathVariable String sessionId) {
        SessionSnapshot snapshot = sandboxService.findSessionSnapshot(sessionId);
        if (snapshot == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(errorBody("SESSION_NOT_FOUND", "Session not found: " + sessionId));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session", toVO(snapshot));
        return ResponseEntity.ok(body);
    }

    /** 强制销毁会话；回执含 成功/失败 + 剩余会话数（默认决策 #7），失败不抛异常原样表达 */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Map<String, Object>> destroySession(@PathVariable String sessionId) {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean destroyed;
        try {
            destroyed = sandboxService.destroySession(sessionId);
        } catch (Exception e) {
            log.error("内部强销失败 sessionId={}: {}", sessionId, e.getMessage(), e);
            body.put("success", false);
            body.put("message", "Destroy failed: " + e.getMessage());
            body.put("remainingSessions", sandboxService.getActiveCount());
            return ResponseEntity.ok(body);
        }
        if (!destroyed) {
            body.put("success", false);
            body.put("message", "Session not found: " + sessionId);
            body.put("remainingSessions", sandboxService.getActiveCount());
            return ResponseEntity.ok(body);
        }
        body.put("success", true);
        body.put("message", "Sandbox session destroyed");
        body.put("remainingSessions", sandboxService.getActiveCount());
        log.info("内部强销完成 sessionId={} remaining={}", sessionId, sandboxService.getActiveCount());
        return ResponseEntity.ok(body);
    }

    /** 触发限流规则立即重拉（管理端保存规则后的补充触发器；失败由定时拉取兜底） */
    @PostMapping("/ratelimit/reload")
    public Map<String, Object> reloadRatelimit() {
        boolean ok = ratelimitService.reload();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", ok);
        if (!ok) {
            body.put("message", "Reload failed; stale cache retained, scheduled refresh will retry");
        }
        return body;
    }

    private Map<String, Object> errorBody(String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        return body;
    }

    /** SessionSnapshot → 契约 JSON Map（时间字符串化，字段名与 Bridge 对齐） */
    private static Map<String, Object> toVO(SessionSnapshot s) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("sessionId", s.getSessionId());
        vo.put("containerId", s.getContainerId());
        vo.put("containerName", s.getContainerName());
        vo.put("createTime", formatTime(s.getCreateTime()));
        vo.put("lastActiveTime", formatTime(s.getLastActiveTime()));
        vo.put("isDefault", Boolean.TRUE.equals(s.getIsDefault()));
        vo.put("ownerClientId", s.getOwnerClientId());
        vo.put("ownerApiKeyId", s.getOwnerApiKeyId());
        vo.put("ownerUserId", s.getOwnerUserId());
        return vo;
    }

    private static String formatTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        LocalDateTime dt = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        return TIME_FORMAT.format(dt);
    }
}
