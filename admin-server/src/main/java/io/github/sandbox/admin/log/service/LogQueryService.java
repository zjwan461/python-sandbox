package io.github.sandbox.admin.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.sandbox.admin.apikey.entity.ClientApiKey;
import io.github.sandbox.admin.apikey.mapper.ClientApiKeyMapper;
import io.github.sandbox.admin.client.entity.ClientApp;
import io.github.sandbox.admin.client.mapper.ClientAppMapper;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.log.dto.ApiLogQuery;
import io.github.sandbox.admin.log.dto.ApiLogVO;
import io.github.sandbox.admin.log.dto.DetectLogQuery;
import io.github.sandbox.admin.log.dto.DetectLogVO;
import io.github.sandbox.admin.log.dto.SandboxLogQuery;
import io.github.sandbox.admin.log.dto.SandboxLogVO;
import io.github.sandbox.admin.log.dto.TraceDetailVO;
import io.github.sandbox.admin.log.entity.ApiLogView;
import io.github.sandbox.admin.log.entity.CodeGuardDetectLogView;
import io.github.sandbox.admin.log.entity.SandboxOperationLogView;
import io.github.sandbox.admin.log.mapper.ApiLogViewMapper;
import io.github.sandbox.admin.log.mapper.CodeGuardDetectLogViewMapper;
import io.github.sandbox.admin.log.mapper.SandboxOperationLogViewMapper;
import io.github.sandbox.admin.rbac.entity.AdminUser;
import io.github.sandbox.admin.rbac.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 调用记录查询服务（T-0032，FR-LOG-01~05/07；design.md §5.5、§8.5、§10.3）。
 *
 * <p>api_log / sandbox_operation_log 为 python-sandbox 写入的既有表，本服务只读查询、
 * 不写入、不修改其结构。数据权限：两表均已注册 SELF 行过滤（T-0021，按 owner_user_id），
 * 普通用户天然只获得本人可见域；管理员/审计员 ALL 域获得全部记录（FR-RBAC-01/02）。</p>
 *
 * <p>截断口径（FR-LOG-04）：写入侧以 {@code ...(truncated)} 后缀标记截断，
 * 本服务派生布尔标记透出，明确不伪装为完整内容。</p>
 */
@Service
@RequiredArgsConstructor
public class LogQueryService {

    /** python-sandbox 写入侧截断后缀（ApiLogAspect / SandboxOperationLogAspect 约定） */
    static final String TRUNCATED_SUFFIX = "...(truncated)";

    private final ApiLogViewMapper apiLogMapper;
    private final SandboxOperationLogViewMapper sandboxLogMapper;
    private final CodeGuardDetectLogViewMapper detectLogMapper;
    private final ClientAppMapper clientAppMapper;
    private final ClientApiKeyMapper apiKeyMapper;
    private final AdminUserMapper adminUserMapper;

    /** API 日志分页（FR-LOG-01，默认时间倒序，FR-LOG-05） */
    public PageResult<ApiLogVO> pageApiLog(ApiLogQuery query) {
        LambdaQueryWrapper<ApiLogView> wrapper = buildApiWrapper(query);
        applyApiOrder(wrapper, query);
        Page<ApiLogView> page = apiLogMapper.selectPage(
                new Page<>(Math.max(1, query.getPageNum()), clampSize(query.getPageSize())), wrapper);
        Map<Long, ClientApp> clientMap = loadClients(page.getRecords().stream()
                .map(ApiLogView::getClientId).collect(Collectors.toList()));
        Map<Long, ClientApiKey> keyMap = loadKeys(page.getRecords().stream()
                .map(ApiLogView::getApiKeyId).collect(Collectors.toList()));
        Map<Long, AdminUser> userMap = loadUsers(page.getRecords().stream()
                .map(ApiLogView::getOwnerUserId).collect(Collectors.toList()));
        List<ApiLogVO> vos = page.getRecords().stream()
                .map(row -> toApiVO(row, clientMap, keyMap, userMap)).toList();
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /**
     * API 日志导出查询（T-0045，FR-LOG-06）：与分页同一筛选/排序/数据权限口径，
     * 一次性取前 cap 条（cap 由调用方控制，防止无界导出）。
     */
    public List<ApiLogVO> listApiLogForExport(ApiLogQuery query, int cap) {
        LambdaQueryWrapper<ApiLogView> wrapper = buildApiWrapper(query);
        applyApiOrder(wrapper, query);
        wrapper.last("LIMIT " + Math.min(Math.max(1, cap), 50000));
        List<ApiLogView> rows = apiLogMapper.selectList(wrapper);
        Map<Long, ClientApp> clientMap = loadClients(rows.stream()
                .map(ApiLogView::getClientId).collect(Collectors.toList()));
        Map<Long, ClientApiKey> keyMap = loadKeys(rows.stream()
                .map(ApiLogView::getApiKeyId).collect(Collectors.toList()));
        Map<Long, AdminUser> userMap = loadUsers(rows.stream()
                .map(ApiLogView::getOwnerUserId).collect(Collectors.toList()));
        return rows.stream().map(r -> toApiVO(r, clientMap, keyMap, userMap)).toList();
    }

    /** 沙箱操作日志导出查询（T-0045，口径同上） */
    public List<SandboxLogVO> listSandboxLogForExport(SandboxLogQuery query, int cap) {
        LambdaQueryWrapper<SandboxOperationLogView> wrapper = buildSandboxWrapper(query);
        applySandboxOrder(wrapper, query);
        wrapper.last("LIMIT " + Math.min(Math.max(1, cap), 50000));
        List<SandboxOperationLogView> rows = sandboxLogMapper.selectList(wrapper);
        Map<Long, ClientApp> clientMap = loadClients(rows.stream()
                .map(SandboxOperationLogView::getClientId).collect(Collectors.toList()));
        Map<Long, ClientApiKey> keyMap = loadKeys(rows.stream()
                .map(SandboxOperationLogView::getApiKeyId).collect(Collectors.toList()));
        Map<Long, AdminUser> userMap = loadUsers(rows.stream()
                .map(SandboxOperationLogView::getOwnerUserId).collect(Collectors.toList()));
        return rows.stream().map(r -> toSandboxVO(r, clientMap, keyMap, userMap)).toList();
    }

    private LambdaQueryWrapper<ApiLogView> buildApiWrapper(ApiLogQuery query) {
        return Wrappers.<ApiLogView>lambdaQuery()
                .ge(query.getBeginTime() != null, ApiLogView::getCreatedAt, query.getBeginTime())
                .le(query.getEndTime() != null, ApiLogView::getCreatedAt, query.getEndTime())
                .eq(query.getApiKeyId() != null, ApiLogView::getApiKeyId, query.getApiKeyId())
                .eq(query.getClientId() != null, ApiLogView::getClientId, query.getClientId())
                .eq(query.getOwnerUserId() != null, ApiLogView::getOwnerUserId, query.getOwnerUserId())
                .eq(StringUtils.hasText(query.getHttpMethod()), ApiLogView::getHttpMethod, query.getHttpMethod())
                .like(StringUtils.hasText(query.getApiPath()), ApiLogView::getApiPath, query.getApiPath())
                .eq(query.getResponseCode() != null, ApiLogView::getResponseCode, query.getResponseCode())
                .eq(StringUtils.hasText(query.getTraceId()), ApiLogView::getTraceId, query.getTraceId())
                .eq(StringUtils.hasText(query.getClientIp()), ApiLogView::getClientIp, query.getClientIp())
                .eq(StringUtils.hasText(query.getSessionId()), ApiLogView::getSessionId, query.getSessionId())
                .eq(query.getRateLimitHit() != null, ApiLogView::getRateLimitHit,
                        query.getRateLimitHit() != null && query.getRateLimitHit() ? 1 : 0);
    }

    private LambdaQueryWrapper<SandboxOperationLogView> buildSandboxWrapper(SandboxLogQuery query) {
        return Wrappers.<SandboxOperationLogView>lambdaQuery()
                .ge(query.getBeginTime() != null, SandboxOperationLogView::getCreatedAt, query.getBeginTime())
                .le(query.getEndTime() != null, SandboxOperationLogView::getCreatedAt, query.getEndTime())
                .eq(StringUtils.hasText(query.getOperationType()), SandboxOperationLogView::getOperationType, query.getOperationType())
                .eq(StringUtils.hasText(query.getResult()), SandboxOperationLogView::getResult, query.getResult())
                .eq(StringUtils.hasText(query.getTraceId()), SandboxOperationLogView::getTraceId, query.getTraceId())
                .eq(StringUtils.hasText(query.getSessionId()), SandboxOperationLogView::getSessionId, query.getSessionId())
                .eq(query.getClientId() != null, SandboxOperationLogView::getClientId, query.getClientId())
                .eq(query.getApiKeyId() != null, SandboxOperationLogView::getApiKeyId, query.getApiKeyId())
                .eq(query.getOwnerUserId() != null, SandboxOperationLogView::getOwnerUserId, query.getOwnerUserId());
    }

    // ===================== CodeGuard 检测记录 =====================

    /** 检测记录分页（默认时间倒序；数据权限由 SELF 行过滤保证） */
    public PageResult<DetectLogVO> pageDetectLog(DetectLogQuery query) {
        LambdaQueryWrapper<CodeGuardDetectLogView> wrapper = buildDetectWrapper(query);
        applyDetectOrder(wrapper, query);
        Page<CodeGuardDetectLogView> page = detectLogMapper.selectPage(
                new Page<>(Math.max(1, query.getPageNum()), clampSize(query.getPageSize())), wrapper);
        Map<Long, ClientApp> clientMap = loadClients(page.getRecords().stream()
                .map(CodeGuardDetectLogView::getClientId).collect(Collectors.toList()));
        Map<Long, ClientApiKey> keyMap = loadKeys(page.getRecords().stream()
                .map(CodeGuardDetectLogView::getApiKeyId).collect(Collectors.toList()));
        Map<Long, AdminUser> userMap = loadUsers(page.getRecords().stream()
                .map(CodeGuardDetectLogView::getOwnerUserId).collect(Collectors.toList()));
        List<DetectLogVO> vos = page.getRecords().stream()
                .map(row -> toDetectVO(row, clientMap, keyMap, userMap)).toList();
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 检测记录详情（经数据权限行过滤；越权=不存在） */
    public DetectLogVO detectLogDetail(Long id) {
        CodeGuardDetectLogView row = detectLogMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "检测记录不存在或无权访问");
        }
        return toDetectVO(row, loadClients(List.of(row.getClientId())),
                loadKeys(List.of(row.getApiKeyId())), loadUsers(List.of(row.getOwnerUserId())));
    }

    /** 检测记录导出查询（与分页同一筛选/排序/数据权限口径，上限 cap 条） */
    public List<DetectLogVO> listDetectLogForExport(DetectLogQuery query, int cap) {
        LambdaQueryWrapper<CodeGuardDetectLogView> wrapper = buildDetectWrapper(query);
        applyDetectOrder(wrapper, query);
        wrapper.last("LIMIT " + Math.min(Math.max(1, cap), 50000));
        List<CodeGuardDetectLogView> rows = detectLogMapper.selectList(wrapper);
        Map<Long, ClientApp> clientMap = loadClients(rows.stream()
                .map(CodeGuardDetectLogView::getClientId).collect(Collectors.toList()));
        Map<Long, ClientApiKey> keyMap = loadKeys(rows.stream()
                .map(CodeGuardDetectLogView::getApiKeyId).collect(Collectors.toList()));
        Map<Long, AdminUser> userMap = loadUsers(rows.stream()
                .map(CodeGuardDetectLogView::getOwnerUserId).collect(Collectors.toList()));
        return rows.stream().map(r -> toDetectVO(r, clientMap, keyMap, userMap)).toList();
    }

    private LambdaQueryWrapper<CodeGuardDetectLogView> buildDetectWrapper(DetectLogQuery query) {
        return Wrappers.<CodeGuardDetectLogView>lambdaQuery()
                .ge(query.getBeginTime() != null, CodeGuardDetectLogView::getCreatedAt, query.getBeginTime())
                .le(query.getEndTime() != null, CodeGuardDetectLogView::getCreatedAt, query.getEndTime())
                .eq(StringUtils.hasText(query.getLabel()), CodeGuardDetectLogView::getLabel, query.getLabel())
                .eq(StringUtils.hasText(query.getDecision()), CodeGuardDetectLogView::getDecision, query.getDecision())
                .eq(StringUtils.hasText(query.getDetectStatus()), CodeGuardDetectLogView::getDetectStatus, query.getDetectStatus())
                .eq(StringUtils.hasText(query.getTraceId()), CodeGuardDetectLogView::getTraceId, query.getTraceId())
                .eq(StringUtils.hasText(query.getSessionId()), CodeGuardDetectLogView::getSessionId, query.getSessionId())
                .eq(query.getClientId() != null, CodeGuardDetectLogView::getClientId, query.getClientId())
                .eq(query.getApiKeyId() != null, CodeGuardDetectLogView::getApiKeyId, query.getApiKeyId())
                .eq(query.getOwnerUserId() != null, CodeGuardDetectLogView::getOwnerUserId, query.getOwnerUserId());
    }

    private void applyDetectOrder(LambdaQueryWrapper<CodeGuardDetectLogView> wrapper, DetectLogQuery query) {
        boolean asc = Boolean.TRUE.equals(query.getAsc());
        String orderBy = query.getOrderBy() == null ? "createdAt" : query.getOrderBy();
        switch (orderBy) {
            case "id" -> wrapper.orderBy(true, asc, CodeGuardDetectLogView::getId);
            case "latencyMs" -> wrapper.orderBy(true, asc, CodeGuardDetectLogView::getLatencyMs);
            default -> wrapper.orderBy(true, asc, CodeGuardDetectLogView::getCreatedAt);
        }
    }

    private DetectLogVO toDetectVO(CodeGuardDetectLogView row, Map<Long, ClientApp> clientMap,
                                   Map<Long, ClientApiKey> keyMap, Map<Long, AdminUser> userMap) {
        DetectLogVO vo = new DetectLogVO();
        vo.setId(row.getId());
        vo.setTraceId(row.getTraceId());
        vo.setSessionId(row.getSessionId());
        vo.setCodeSnippet(row.getCodeSnippet());
        vo.setCodeLength(row.getCodeLength());
        vo.setModelName(row.getModelName());
        vo.setLabel(row.getLabel());
        vo.setDangerous(row.getDangerous());
        vo.setRawOutput(row.getRawOutput());
        vo.setDetectStatus(row.getDetectStatus());
        vo.setDecision(row.getDecision());
        vo.setLatencyMs(row.getLatencyMs());
        vo.setErrorMessage(row.getErrorMessage());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setClientId(row.getClientId());
        ClientApp client = row.getClientId() == null ? null : clientMap.get(row.getClientId());
        if (client != null) {
            vo.setClientCode(client.getClientCode());
        }
        vo.setApiKeyId(row.getApiKeyId());
        ClientApiKey key = row.getApiKeyId() == null ? null : keyMap.get(row.getApiKeyId());
        if (key != null) {
            vo.setApiKeyLabel(key.getName() + "（" + key.getKeyPrefix() + "…" + key.getKeySuffixMask() + "）");
        }
        vo.setOwnerUserId(row.getOwnerUserId());
        AdminUser user = row.getOwnerUserId() == null ? null : userMap.get(row.getOwnerUserId());
        if (user != null) {
            vo.setOwnerUserName(user.getUsername());
        }
        return vo;
    }

    /** API 日志详情（经数据权限行过滤；越权=不存在） */
    public ApiLogVO apiLogDetail(Long id) {
        ApiLogView row = apiLogMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "调用记录不存在或无权访问");
        }
        return toApiVO(row, loadClients(List.of(row.getClientId())),
                loadKeys(List.of(row.getApiKeyId())), loadUsers(List.of(row.getOwnerUserId())));
    }

    /** 沙箱操作日志分页（FR-LOG-02） */
    public PageResult<SandboxLogVO> pageSandboxLog(SandboxLogQuery query) {
        LambdaQueryWrapper<SandboxOperationLogView> wrapper = buildSandboxWrapper(query);
        applySandboxOrder(wrapper, query);
        Page<SandboxOperationLogView> page = sandboxLogMapper.selectPage(
                new Page<>(Math.max(1, query.getPageNum()), clampSize(query.getPageSize())), wrapper);
        Map<Long, ClientApp> clientMap = loadClients(page.getRecords().stream()
                .map(SandboxOperationLogView::getClientId).collect(Collectors.toList()));
        Map<Long, ClientApiKey> keyMap = loadKeys(page.getRecords().stream()
                .map(SandboxOperationLogView::getApiKeyId).collect(Collectors.toList()));
        Map<Long, AdminUser> userMap = loadUsers(page.getRecords().stream()
                .map(SandboxOperationLogView::getOwnerUserId).collect(Collectors.toList()));
        List<SandboxLogVO> vos = page.getRecords().stream()
                .map(row -> toSandboxVO(row, clientMap, keyMap, userMap)).toList();
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 沙箱操作日志详情 */
    public SandboxLogVO sandboxLogDetail(Long id) {
        SandboxOperationLogView row = sandboxLogMapper.selectById(id);
        if (row == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "操作日志不存在或无权访问");
        }
        return toSandboxVO(row, loadClients(List.of(row.getClientId())),
                loadKeys(List.of(row.getApiKeyId())), loadUsers(List.of(row.getOwnerUserId())));
    }

    /**
     * 按 traceId 聚合链路详情（FR-LOG-03）：同屏返回 API 日志与全部沙箱操作日志。
     * 两表查询各自经过 SELF 行过滤，普通用户仅聚合到本人记录（故事 C）。
     */
    public TraceDetailVO traceDetail(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "traceId 不能为空");
        }
        List<ApiLogView> apiRows = apiLogMapper.selectList(Wrappers.<ApiLogView>lambdaQuery()
                .eq(ApiLogView::getTraceId, traceId)
                .orderByAsc(ApiLogView::getCreatedAt));
        List<SandboxOperationLogView> opRows = sandboxLogMapper.selectList(
                Wrappers.<SandboxOperationLogView>lambdaQuery()
                        .eq(SandboxOperationLogView::getTraceId, traceId)
                        .orderByAsc(SandboxOperationLogView::getCreatedAt));
        if (apiRows.isEmpty() && opRows.isEmpty()) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "该 traceId 无可见的链路记录");
        }
        List<Long> clientIds = Stream.concat(apiRows.stream().map(ApiLogView::getClientId),
                opRows.stream().map(SandboxOperationLogView::getClientId)).collect(Collectors.toList());
        List<Long> apiKeyIds = Stream.concat(apiRows.stream().map(ApiLogView::getApiKeyId),
                opRows.stream().map(SandboxOperationLogView::getApiKeyId)).collect(Collectors.toList());
        List<Long> userIds = Stream.concat(apiRows.stream().map(ApiLogView::getOwnerUserId),
                opRows.stream().map(SandboxOperationLogView::getOwnerUserId)).collect(Collectors.toList());
        Map<Long, ClientApp> clientMap = loadClients(clientIds);
        Map<Long, ClientApiKey> keyMap = loadKeys(apiKeyIds);
        Map<Long, AdminUser> userMap = loadUsers(userIds);

        TraceDetailVO detail = new TraceDetailVO();
        detail.setTraceId(traceId);
        detail.setApiLogs(apiRows.stream().map(r -> toApiVO(r, clientMap, keyMap, userMap)).toList());
        detail.setOperationLogs(opRows.stream().map(r -> toSandboxVO(r, clientMap, keyMap, userMap)).toList());
        return detail;
    }

    /**
     * 按会话ID查最近日志（T-0037，FR-SESSION-03）：API 日志与沙箱操作日志各取最近 limit 条，
     * 时间倒序；数据权限由两表 SELF 行过滤天然保证（普通用户只获本人记录）。
     */
    public Map<String, Object> recentBySession(String sessionId, int limit) {
        int size = Math.min(Math.max(1, limit), 100);
        List<ApiLogView> apiRows = apiLogMapper.selectList(Wrappers.<ApiLogView>lambdaQuery()
                .eq(ApiLogView::getSessionId, sessionId)
                .orderByDesc(ApiLogView::getCreatedAt)
                .last("LIMIT " + size));
        List<SandboxOperationLogView> opRows = sandboxLogMapper.selectList(
                Wrappers.<SandboxOperationLogView>lambdaQuery()
                        .eq(SandboxOperationLogView::getSessionId, sessionId)
                        .orderByDesc(SandboxOperationLogView::getCreatedAt)
                        .last("LIMIT " + size));
        List<Long> clientIds = Stream.concat(apiRows.stream().map(ApiLogView::getClientId),
                opRows.stream().map(SandboxOperationLogView::getClientId)).collect(Collectors.toList());
        List<Long> apiKeyIds = Stream.concat(apiRows.stream().map(ApiLogView::getApiKeyId),
                opRows.stream().map(SandboxOperationLogView::getApiKeyId)).collect(Collectors.toList());
        List<Long> userIds = Stream.concat(apiRows.stream().map(ApiLogView::getOwnerUserId),
                opRows.stream().map(SandboxOperationLogView::getOwnerUserId)).collect(Collectors.toList());
        Map<Long, ClientApp> clientMap = loadClients(clientIds);
        Map<Long, ClientApiKey> keyMap = loadKeys(apiKeyIds);
        Map<Long, AdminUser> userMap = loadUsers(userIds);
        return Map.of(
                "apiLogs", apiRows.stream().map(r -> toApiVO(r, clientMap, keyMap, userMap)).toList(),
                "operationLogs", opRows.stream().map(r -> toSandboxVO(r, clientMap, keyMap, userMap)).toList());
    }

    // ===================== internal =====================

    private Map<Long, ClientApp> loadClients(List<Long> ids) {
        List<Long> distinct = dedup(ids);
        return distinct.isEmpty() ? Map.of()
                : clientAppMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(ClientApp::getId, Function.identity()));
    }

    private Map<Long, ClientApiKey> loadKeys(List<Long> ids) {
        List<Long> distinct = dedup(ids);
        return distinct.isEmpty() ? Map.of()
                : apiKeyMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(ClientApiKey::getId, Function.identity()));
    }

    private Map<Long, AdminUser> loadUsers(List<Long> ids) {
        List<Long> distinct = dedup(ids);
        return distinct.isEmpty() ? Map.of()
                : adminUserMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(AdminUser::getId, Function.identity()));
    }

    private List<Long> dedup(List<Long> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
    }

    private ApiLogVO toApiVO(ApiLogView row, Map<Long, ClientApp> clientMap,
                             Map<Long, ClientApiKey> keyMap, Map<Long, AdminUser> userMap) {
        ApiLogVO vo = new ApiLogVO();
        vo.setId(row.getId());
        vo.setTraceId(row.getTraceId());
        vo.setSessionId(row.getSessionId());
        vo.setApiPath(row.getApiPath());
        vo.setHttpMethod(row.getHttpMethod());
        vo.setRequestParams(row.getRequestParams());
        vo.setRequestParamsTruncated(isTruncated(row.getRequestParams()));
        vo.setResponseCode(row.getResponseCode());
        vo.setExecutionTime(row.getExecutionTime());
        vo.setClientIp(row.getClientIp());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setClientId(row.getClientId());
        ClientApp client = row.getClientId() == null ? null : clientMap.get(row.getClientId());
        if (client != null) {
            vo.setClientCode(client.getClientCode());
        }
        vo.setApiKeyId(row.getApiKeyId());
        ClientApiKey key = row.getApiKeyId() == null ? null : keyMap.get(row.getApiKeyId());
        if (key != null) {
            vo.setApiKeyLabel(key.getName() + "（" + key.getKeyPrefix() + "…" + key.getKeySuffixMask() + "）");
        }
        vo.setOwnerUserId(row.getOwnerUserId());
        AdminUser user = row.getOwnerUserId() == null ? null : userMap.get(row.getOwnerUserId());
        if (user != null) {
            vo.setOwnerUserName(user.getUsername());
        }
        vo.setRateLimitHit(row.getRateLimitHit());
        vo.setRateLimitRuleId(row.getRateLimitRuleId());
        return vo;
    }

    private SandboxLogVO toSandboxVO(SandboxOperationLogView row, Map<Long, ClientApp> clientMap,
                                     Map<Long, ClientApiKey> keyMap, Map<Long, AdminUser> userMap) {
        SandboxLogVO vo = new SandboxLogVO();
        vo.setId(row.getId());
        vo.setTraceId(row.getTraceId());
        vo.setSessionId(row.getSessionId());
        vo.setOperationType(row.getOperationType());
        vo.setOperationContent(row.getOperationContent());
        vo.setOperationContentTruncated(isTruncated(row.getOperationContent()));
        vo.setResult(row.getResult());
        vo.setExitCode(row.getExitCode());
        vo.setStdout(row.getStdout());
        vo.setStdoutTruncated(isTruncated(row.getStdout()));
        vo.setStderr(row.getStderr());
        vo.setStderrTruncated(isTruncated(row.getStderr()));
        vo.setExecutionTime(row.getExecutionTime());
        vo.setErrorMessage(row.getErrorMessage());
        vo.setCreatedAt(row.getCreatedAt());
        vo.setClientId(row.getClientId());
        ClientApp client = row.getClientId() == null ? null : clientMap.get(row.getClientId());
        if (client != null) {
            vo.setClientCode(client.getClientCode());
        }
        vo.setApiKeyId(row.getApiKeyId());
        ClientApiKey key = row.getApiKeyId() == null ? null : keyMap.get(row.getApiKeyId());
        if (key != null) {
            vo.setApiKeyLabel(key.getName() + "（" + key.getKeyPrefix() + "…" + key.getKeySuffixMask() + "）");
        }
        vo.setOwnerUserId(row.getOwnerUserId());
        AdminUser user = row.getOwnerUserId() == null ? null : userMap.get(row.getOwnerUserId());
        if (user != null) {
            vo.setOwnerUserName(user.getUsername());
        }
        return vo;
    }

    private boolean isTruncated(String value) {
        return value != null && value.endsWith(TRUNCATED_SUFFIX);
    }

    private void applyApiOrder(LambdaQueryWrapper<ApiLogView> wrapper, ApiLogQuery query) {
        boolean asc = Boolean.TRUE.equals(query.getAsc());
        String orderBy = query.getOrderBy() == null ? "createdAt" : query.getOrderBy();
        switch (orderBy) {
            case "id" -> wrapper.orderBy(true, asc, ApiLogView::getId);
            case "executionTime" -> wrapper.orderBy(true, asc, ApiLogView::getExecutionTime);
            case "responseCode" -> wrapper.orderBy(true, asc, ApiLogView::getResponseCode);
            default -> wrapper.orderBy(true, asc, ApiLogView::getCreatedAt);
        }
    }

    private void applySandboxOrder(LambdaQueryWrapper<SandboxOperationLogView> wrapper, SandboxLogQuery query) {
        boolean asc = Boolean.TRUE.equals(query.getAsc());
        String orderBy = query.getOrderBy() == null ? "createdAt" : query.getOrderBy();
        switch (orderBy) {
            case "id" -> wrapper.orderBy(true, asc, SandboxOperationLogView::getId);
            case "executionTime" -> wrapper.orderBy(true, asc, SandboxOperationLogView::getExecutionTime);
            default -> wrapper.orderBy(true, asc, SandboxOperationLogView::getCreatedAt);
        }
    }

    private long clampSize(long size) {
        return Math.min(Math.max(1, size), 200);
    }
}
