package io.github.sandbox.admin.bridge.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sandbox.admin.bridge.config.SandboxBridgeProperties;
import io.github.sandbox.admin.bridge.dto.SandboxSessionVO;
import io.github.sandbox.admin.bridge.dto.SessionDestroyResultVO;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.filter.AdminTraceFilter;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * admin-server → python-sandbox 内部接口统一封装（T-0027，design.md §6.3、§10.4）。
 *
 * <p>硬约束：</p>
 * <ul>
 *   <li>业务 Controller 不直接硬编码 python-sandbox URL 或凭证，一律经本网关调用。</li>
 *   <li>每次内部调用只使用配置中的同一 {@code X-Admin-Internal-Token}（ENV 可覆盖，不入库）。</li>
 *   <li>客户端 ApiKey 绝不发送到该网关（内部凭证与业务凭证完全分离，默认决策 #9）。</li>
 *   <li>内部调用失败转换为管理端明确业务错误（50001/50002），不暴露内部堆栈。</li>
 *   <li>不导入 python-sandbox 的任何类；跨工程仅以 JSON 契约交互。</li>
 * </ul>
 *
 * <p>内部接口契约（design.md §10.4，python-sandbox 侧由 T-0025/T-0026 实现）：</p>
 * <ul>
 *   <li>{@code GET    /internal/sandbox/sessions} → {@code {"sessions":[SandboxSessionVO...]}}</li>
 *   <li>{@code GET    /internal/sandbox/sessions/{sessionId}/detail} → {@code {"session":SandboxSessionVO}}</li>
 *   <li>{@code DELETE /internal/sandbox/sessions/{sessionId}} → {@code {"success":bool,"message":str,"remainingSessions":int}}</li>
 *   <li>{@code POST   /internal/sandbox/ratelimit/reload} → {@code {"success":bool,"message":str}}</li>
 * </ul>
 */
@Slf4j
@Component
public class SandboxBridgeClient {

    /** 内部接口统一前缀（python-sandbox 侧独立鉴权通道） */
    public static final String INTERNAL_PREFIX = "/internal/sandbox";

    /** 内部凭证 Header 名（design.md §3.2 命名空间约定） */
    public static final String HEADER_INTERNAL_TOKEN = "X-Admin-Internal-Token";

    private final SandboxBridgeProperties properties;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public SandboxBridgeClient(SandboxBridgeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(15000);
        this.restTemplate = new RestTemplate(factory);
    }

    /** 列出 python-sandbox 当前进程内活跃会话（内存快照） */
    public List<SandboxSessionVO> listSessions() {
        JsonNode root = exchange(HttpMethod.GET, INTERNAL_PREFIX + "/sessions", null);
        JsonNode sessions = root.path("sessions");
        if (sessions.isMissingNode() || sessions.isNull()) {
            // 兼容直接返回数组的形态
            return fromJson(root.isArray() ? root : root.path("data"));
        }
        return fromJson(sessions);
    }

    /** 查询单个会话详情（不存在返回 null） */
    /** 查询单个会话详情（不存在返回 null） */
    public SandboxSessionVO getSession(String sessionId) {
        try {
            JsonNode root = exchange(HttpMethod.GET,
                    INTERNAL_PREFIX + "/sessions/" + urlSafe(sessionId) + "/detail", null);
            JsonNode node = root.has("session") ? root.path("session") : root;
            if (node.isMissingNode() || node.isNull() || node.isEmpty()) {
                return null;
            }
            return objectMapper.treeToValue(node, SandboxSessionVO.class);
        } catch (BusinessException e) {
            if (e.getCode() == ErrorCode.SESSION_NOT_FOUND.getCode()) {
                return null;
            }
            throw e;
        } catch (Exception e) {
            log.error("解析 python-sandbox 会话详情失败 sessionId={}", sessionId, e);
            throw new BusinessException(ErrorCode.SANDBOX_BRIDGE_ERROR, "沙箱服务返回数据无法解析");
        }
    }
    /** 强制销毁会话；回执含 成功/失败 + 剩余会话数（默认决策 #7），失败不抛异常、原样表达 */
    public SessionDestroyResultVO destroySession(String sessionId) {
        SessionDestroyResultVO result = new SessionDestroyResultVO();
        result.setSessionId(sessionId);
        try {
            JsonNode root = exchange(HttpMethod.DELETE,
                    INTERNAL_PREFIX + "/sessions/" + urlSafe(sessionId), null);
            result.setSuccess(root.path("success").asBoolean(false));
            result.setMessage(root.path("message").asText(null));
            JsonNode remaining = root.path("remainingSessions");
            result.setRemainingSessions(remaining.isNumber() ? remaining.asInt() : null);
            if (!result.isSuccess() && result.getMessage() == null) {
                result.setMessage("python-sandbox 返回销毁失败");
            }
            return result;
        } catch (BusinessException e) {
            if (e.getCode() == ErrorCode.SESSION_NOT_FOUND.getCode()) {
                result.setSuccess(false);
                result.setMessage(ErrorCode.SESSION_NOT_FOUND.getMessage());
                return result;
            }
            // 网关/凭证类错误继续上抛，转换为管理端明确业务错误
            throw e;
        }
    }

    /** 触发 python-sandbox 立即拉取最新限流规则（T-0030 保存后调用，定时拉取的补充触发器） */
    public boolean reloadRatelimitRules() {
        JsonNode root = exchange(HttpMethod.POST, INTERNAL_PREFIX + "/ratelimit/reload", null);
        return root.path("success").asBoolean(true);
    }

    // ===================== internal =====================

    private List<SandboxSessionVO> fromJson(JsonNode arrayNode) {
        try {
            if (arrayNode == null || arrayNode.isMissingNode() || arrayNode.isNull()) {
                return List.of();
            }
            return objectMapper.readerForListOf(SandboxSessionVO.class).readValue(arrayNode);
        } catch (Exception e) {
            log.error("解析 python-sandbox 会话快照失败", e);
            throw new BusinessException(ErrorCode.SANDBOX_BRIDGE_ERROR, "沙箱服务返回数据无法解析");
        }
    }

    private JsonNode exchange(HttpMethod method, String path, Object body) {
        String url = trimTrailingSlash(properties.getSandbox().getBaseUrl()) + path;
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_INTERNAL_TOKEN, properties.getInternal().getToken());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        String traceId = MDC.get(AdminTraceFilter.MDC_KEY);
        if (traceId != null) {
            headers.set(AdminTraceFilter.TRACE_HEADER, traceId);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, method, entity, String.class);
            if (resp.getBody() == null || resp.getBody().isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(resp.getBody());
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 401 || e.getStatusCode().value() == 403) {
                log.error("python-sandbox 拒绝内部凭证 {} {} -> {}", method, path, e.getStatusCode());
                throw new BusinessException(ErrorCode.SANDBOX_BRIDGE_UNAUTHORIZED);
            }
            if (e.getStatusCode().value() == 404) {
                throw new BusinessException(ErrorCode.SESSION_NOT_FOUND);
            }
            log.error("python-sandbox 内部接口返回错误 {} {} -> {} {}", method, path,
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.SANDBOX_BRIDGE_ERROR,
                    "沙箱服务返回错误（HTTP " + e.getStatusCode().value() + "）");
        } catch (ResourceAccessException e) {
            log.error("python-sandbox 内部接口不可达 {} {}: {}", method, url, e.getMessage());
            throw new BusinessException(ErrorCode.SANDBOX_BRIDGE_ERROR, "沙箱服务不可达，请稍后重试");
        } catch (Exception e) {
            log.error("python-sandbox 内部调用异常 {} {}", method, path, e);
            throw new BusinessException(ErrorCode.SANDBOX_BRIDGE_ERROR);
        }
    }

    private String urlSafe(String segment) {
        return segment == null ? "" : segment.replaceAll("[^a-zA-Z0-9_\\-]", "");
    }

    private String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(ErrorCode.SANDBOX_BRIDGE_ERROR, "未配置 admin.sandbox.base-url");
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
