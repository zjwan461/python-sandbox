package io.github.sandbox.admin.session.service;

import io.github.sandbox.admin.apikey.entity.ClientApiKey;
import io.github.sandbox.admin.apikey.mapper.ClientApiKeyMapper;
import io.github.sandbox.admin.bridge.client.SandboxBridgeClient;
import io.github.sandbox.admin.bridge.dto.SandboxSessionVO;
import io.github.sandbox.admin.bridge.dto.SessionDestroyResultVO;
import io.github.sandbox.admin.client.entity.ClientApp;
import io.github.sandbox.admin.client.mapper.ClientAppMapper;
import io.github.sandbox.admin.common.datapermission.DataPermissionIgnoreHolder;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.security.AdminLoginUser;
import io.github.sandbox.admin.common.security.DataScopes;
import io.github.sandbox.admin.common.security.SecurityUtils;
import io.github.sandbox.admin.log.service.LogQueryService;
import io.github.sandbox.admin.rbac.entity.AdminUser;
import io.github.sandbox.admin.rbac.mapper.AdminUserMapper;
import io.github.sandbox.admin.session.dto.SessionBatchDestroyRequest;
import io.github.sandbox.admin.session.dto.SessionQuery;
import io.github.sandbox.admin.session.dto.SessionRelatedLogsVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 运行中会话管理业务（T-0031，FR-SESSION-01/04；design.md §8.4、§10.4、§11.4）。
 *
 * <p>数据来源：python-sandbox 进程内活跃会话内存快照（仅经 {@link SandboxBridgeClient}
 * 的 /internal/** 通道获取，不 import 其类、不直连 Docker）。服务重启后无法枚举的
 * 孤儿会话不被伪造（T-0025 验收）。</p>
 *
 * <p>数据权限（FR-RBAC-02/03）：会话本身不落库，无法走 MyBatis 行过滤，
 * 故本 Service 显式实现与 {@code AdminDataPermissionHandler} 相同口径的
 * SELF 过滤——归属键 owner = COALESCE(apiKey.boundUserId, clientApp.ownerUserId)：
 * 普通用户仅能看到归属为自己的会话；归属不可解析（匿名/无归属）的会话对普通用户不展示。</p>
 *
 * <p>强销：仅 session:force 权限（种子授权=超管/管理员）；结果原样回传，
 * 失败不从前端移除记录（验收口径），成功/失败均由 Controller 的 @OperationLog 落审计。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionAdminService {

    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SandboxBridgeClient bridgeClient;
    private final ClientAppMapper clientAppMapper;
    private final ClientApiKeyMapper apiKeyMapper;
    private final AdminUserMapper adminUserMapper;
    private final LogQueryService logQueryService;

    /** 活跃会话分页列表（筛选 + SELF 数据权限 + 归属富化） */
    public PageResult<SandboxSessionVO> page(SessionQuery query) {
        List<SandboxSessionVO> sessions = visibleSessions(query);
        // 内存分页
        long pageNum = Math.max(1, query.getPageNum());
        long pageSize = Math.min(Math.max(1, query.getPageSize()), 200);
        int from = (int) Math.min((pageNum - 1) * pageSize, sessions.size());
        int to = (int) Math.min(from + pageSize, sessions.size());
        return new PageResult<>(sessions.subList(from, to), sessions.size(), pageNum, pageSize);
    }

    /** 会话详情（不存在或越权统一 40001 语义，不透出他方数据） */
    public SandboxSessionVO detail(String sessionId) {
        List<SandboxSessionVO> visible = visibleSessions(new SessionQuery());
        return visible.stream()
                .filter(s -> Objects.equals(s.getSessionId(), sessionId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
    }

    /**
     * 强制销毁（FR-SESSION-04）：
     * 先按可见域校验目标会话归属（普通用户无强销权限码，天然到不了这里；
     * ALL 域可强销任意会话），再经 Bridge 调用内部强销接口，回执原样返回。
     */
    public SessionDestroyResultVO destroy(String sessionId) {
        SandboxSessionVO target = detail(sessionId);
        SessionDestroyResultVO result = bridgeClient.destroySession(sessionId);
        // 对象名口径（默认决策 #12）：容器名回填进 result 前已由 Controller 审计切面记录入参
        log.info("会话强销 sessionId={} container={} success={} remaining={}",
                target.getSessionId(), target.getContainerName(), result.isSuccess(), result.getRemainingSessions());
        return result;
    }

    /**
     * 会话关联日志（T-0037，FR-SESSION-03）：先按可见域校验会话归属（越权/不存在 40001），
     * 再取该会话最近 API 日志与沙箱操作日志（各自时间倒序，默认 20 条），
     * 日志查询遵守两表 SELF 数据权限过滤。
     */
    public SessionRelatedLogsVO relatedLogs(String sessionId, int limit) {
        SandboxSessionVO session = detail(sessionId);
        Map<String, Object> recent = logQueryService.recentBySession(sessionId, limit);
        SessionRelatedLogsVO vo = new SessionRelatedLogsVO();
        vo.setSession(session);
        @SuppressWarnings("unchecked")
        java.util.List<io.github.sandbox.admin.log.dto.ApiLogVO> apiLogs =
                (java.util.List<io.github.sandbox.admin.log.dto.ApiLogVO>) recent.get("apiLogs");
        @SuppressWarnings("unchecked")
        java.util.List<io.github.sandbox.admin.log.dto.SandboxLogVO> opLogs =
                (java.util.List<io.github.sandbox.admin.log.dto.SandboxLogVO>) recent.get("operationLogs");
        vo.setApiLogs(apiLogs);
        vo.setOperationLogs(opLogs);
        return vo;
    }

    /**
     * 无活跃会话批量清理（T-0044，FR-SESSION-06）。
     *
     * <p>目标集合口径：</p>
     * <ul>
     *   <li>inactiveMinutes 自动筛选：仅取"最后活跃早于阈值"的会话，且默认会话
     *       不被隐式纳入（验收口径）。</li>
     *   <li>sessionIds 显式清单：仅接受当前用户可见域内的会话（不可见项计入失败回执，
     *       不虚构成功）；显式包含默认会话时由前端完成高危二次确认。</li>
     * </ul>
     * <p>逐项调用 Bridge 强销并原样聚合逐项结果；整体动作与逐项结果由 Controller 的
     * @OperationLog 摘要进入管理端审计。</p>
     */
    public List<SessionDestroyResultVO> batchDestroy(SessionBatchDestroyRequest request) {
        List<SandboxSessionVO> visible = visibleSessions(new SessionQuery());
        List<SandboxSessionVO> targets;
        if (request.getSessionIds() != null && !request.getSessionIds().isEmpty()) {
            targets = visible.stream()
                    .filter(s -> request.getSessionIds().contains(s.getSessionId()))
                    .toList();
        } else if (request.getInactiveMinutes() != null && request.getInactiveMinutes() > 0) {
            SessionQuery q = new SessionQuery();
            q.setInactiveMinutes(request.getInactiveMinutes());
            targets = visibleSessions(q).stream()
                    .filter(s -> !Boolean.TRUE.equals(s.getIsDefault())) // 默认会话不被隐式纳入
                    .toList();
        } else {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "必须提供不活跃阈值（分钟）或显式会话清单");
        }
        List<SessionDestroyResultVO> results = new java.util.ArrayList<>();
        // 显式清单中不可见/不存在的项：计入失败回执（不静默丢弃）
        if (request.getSessionIds() != null) {
            List<String> found = targets.stream().map(SandboxSessionVO::getSessionId).toList();
            for (String sid : request.getSessionIds()) {
                if (!found.contains(sid)) {
                    SessionDestroyResultVO miss = new SessionDestroyResultVO();
                    miss.setSessionId(sid);
                    miss.setSuccess(false);
                    miss.setMessage("会话不存在、已销毁或无权访问");
                    results.add(miss);
                }
            }
        }
        for (SandboxSessionVO t : targets) {
            SessionDestroyResultVO r;
            try {
                r = bridgeClient.destroySession(t.getSessionId());
            } catch (Exception e) {
                r = new SessionDestroyResultVO();
                r.setSessionId(t.getSessionId());
                r.setSuccess(false);
                r.setMessage("强销调用失败：" + e.getMessage());
            }
            results.add(r);
        }
        log.info("批量清理会话：目标={} 成功={}", results.size(),
                results.stream().filter(SessionDestroyResultVO::isSuccess).count());
        return results;
    }

    /** 批量清理预览：按阈值统计目标数量（前端确认弹窗"目标数量确认"用，不含默认会话） */
    public long countInactiveTargets(Integer inactiveMinutes) {
        if (inactiveMinutes == null || inactiveMinutes <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不活跃阈值必须为正整数分钟");
        }
        SessionQuery q = new SessionQuery();
        q.setInactiveMinutes(inactiveMinutes);
        return visibleSessions(q).stream()
                .filter(s -> !Boolean.TRUE.equals(s.getIsDefault()))
                .count();
    }

    // ===================== internal =====================

    private List<SandboxSessionVO> visibleSessions(SessionQuery query) {
        List<SandboxSessionVO> sessions = bridgeClient.listSessions();
        if (sessions.isEmpty()) {
            return List.of();
        }
        AdminLoginUser me = SecurityUtils.getLoginUser();
        enrichOwners(sessions);

        // SELF 数据权限：普通用户仅保留归属=自己的会话（owner 解析口径同 T-0021）
        List<SandboxSessionVO> scoped = sessions;
        if (!me.isAllScope()) {
            scoped = sessions.stream()
                    .filter(s -> Objects.equals(s.getOwnerUserId(), me.getUserId()))
                    .toList();
        }

        // 业务筛选
        if (query.getClientId() != null) {
            scoped = scoped.stream().filter(s -> Objects.equals(s.getOwnerClientId(), query.getClientId())).toList();
        }
        if (query.getApiKeyId() != null) {
            scoped = scoped.stream().filter(s -> Objects.equals(s.getOwnerApiKeyId(), query.getApiKeyId())).toList();
        }
        if (query.getOwnerUserId() != null) {
            scoped = scoped.stream().filter(s -> Objects.equals(s.getOwnerUserId(), query.getOwnerUserId())).toList();
        }
        if (query.getIsDefault() != null) {
            scoped = scoped.stream().filter(s -> query.getIsDefault().equals(Boolean.TRUE.equals(s.getIsDefault()))).toList();
        }
        if (query.getInactiveMinutes() != null) {
            LocalDateTime cutoff = LocalDateTime.now().minusMinutes(query.getInactiveMinutes());
            scoped = scoped.stream()
                    .filter(s -> parseTs(s.getLastActiveTime()) == null
                            || parseTs(s.getLastActiveTime()).isBefore(cutoff))
                    .toList();
        }
        // T-0037：会话ID（精确）与会话创建时间范围筛选（字符串 yyyy-MM-dd HH:mm:ss 同格式可比较）
        if (StringUtils.hasText(query.getSessionId())) {
            scoped = scoped.stream()
                    .filter(s -> s.getSessionId() != null && s.getSessionId().contains(query.getSessionId().trim()))
                    .toList();
        }
        if (StringUtils.hasText(query.getCreatedBegin())) {
            scoped = scoped.stream()
                    .filter(s -> s.getCreateTime() != null && s.getCreateTime().compareTo(query.getCreatedBegin()) >= 0)
                    .toList();
        }
        if (StringUtils.hasText(query.getCreatedEnd())) {
            scoped = scoped.stream()
                    .filter(s -> s.getCreateTime() != null && s.getCreateTime().compareTo(query.getCreatedEnd()) <= 0)
                    .toList();
        }
        // 默认按最后活跃时间倒序（新鲜的在前）
        return scoped.stream()
                .sorted(Comparator.comparing((SandboxSessionVO s) -> parseTs(s.getLastActiveTime()),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    /**
     * 归属富化与口径对齐：ownerUserId 为空时按 er-alignment §3 口径
     * owner = COALESCE(apiKey.boundUserId, clientApp.ownerUserId) 解析回填，
     * 同时补充 clientCode / apiKeyLabel / userName 展示字段。
     */
    private void enrichOwners(List<SandboxSessionVO> sessions) {
        List<Long> clientIds = sessions.stream().map(SandboxSessionVO::getOwnerClientId)
                .filter(Objects::nonNull).distinct().toList();
        List<Long> apiKeyIds = sessions.stream().map(SandboxSessionVO::getOwnerApiKeyId)
                .filter(Objects::nonNull).distinct().toList();
        // ALL/SELF 均由各自 Mapper 查询天然受行过滤保护；此处批量查询失败降级为不富化（不阻断列表）
        Map<Long, ClientApp> clientMap = Map.of();
        Map<Long, ClientApiKey> keyMap = Map.of();
        try {
            clientMap = clientIds.isEmpty() ? Map.of()
                    : DataPermissionIgnoreHolder.runIgnored(() -> clientAppMapper.selectBatchIds(clientIds)).stream()
                    .collect(Collectors.toMap(ClientApp::getId, Function.identity()));
            keyMap = apiKeyIds.isEmpty() ? Map.of()
                    : DataPermissionIgnoreHolder.runIgnored(() -> apiKeyMapper.selectBatchIds(apiKeyIds)).stream()
                    .collect(Collectors.toMap(ClientApiKey::getId, Function.identity()));
        } catch (Exception e) {
            log.warn("会话归属富化失败（降级为仅原始字段）：{}", e.getMessage());
        }
        for (SandboxSessionVO s : sessions) {
            ClientApp app = s.getOwnerClientId() == null ? null : clientMap.get(s.getOwnerClientId());
            if (app != null) {
                s.setOwnerClientCode(app.getClientCode());
            }
            ClientApiKey key = s.getOwnerApiKeyId() == null ? null : keyMap.get(s.getOwnerApiKeyId());
            if (key != null) {
                s.setOwnerApiKeyLabel(key.getName() + "（" + key.getKeyPrefix() + "…" + key.getKeySuffixMask() + "）");
            }
            // ownerUserId 缺失时按归属链回填（ApiKey 绑定用户 → 客户端归属用户）
            if (s.getOwnerUserId() == null) {
                if (key != null && key.getBoundUserId() != null) {
                    s.setOwnerUserId(key.getBoundUserId());
                } else if (app != null) {
                    s.setOwnerUserId(app.getOwnerUserId());
                }
            }
        }
        // 用户名单次批量补齐
        List<Long> userIds = sessions.stream().map(SandboxSessionVO::getOwnerUserId)
                .filter(Objects::nonNull).distinct().toList();
        if (!userIds.isEmpty()) {
            try {
                Map<Long, AdminUser> userMap = adminUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(AdminUser::getId, Function.identity()));
                sessions.forEach(s -> {
                    AdminUser u = s.getOwnerUserId() == null ? null : userMap.get(s.getOwnerUserId());
                    if (u != null) {
                        s.setOwnerUserName(u.getUsername());
                    }
                });
            } catch (Exception e) {
                log.warn("会话归属用户名补齐失败：{}", e.getMessage());
            }
        }
    }

    private LocalDateTime parseTs(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), TS_FORMAT);
        } catch (Exception e) {
            return null;
        }
    }
}
