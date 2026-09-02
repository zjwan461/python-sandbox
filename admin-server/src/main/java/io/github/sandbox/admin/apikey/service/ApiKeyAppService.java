package io.github.sandbox.admin.apikey.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.sandbox.admin.apikey.dto.ApiKeyCreateVO;
import io.github.sandbox.admin.apikey.dto.ApiKeyQuery;
import io.github.sandbox.admin.apikey.dto.ApiKeyUpsertRequest;
import io.github.sandbox.admin.apikey.dto.ApiKeyVO;
import io.github.sandbox.admin.apikey.entity.ClientApiKey;
import io.github.sandbox.admin.apikey.mapper.ClientApiKeyMapper;
import io.github.sandbox.admin.apikey.util.ApiKeyGenerator;
import io.github.sandbox.admin.client.entity.ClientApp;
import io.github.sandbox.admin.client.service.ClientAppService;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.security.AdminLoginUser;
import io.github.sandbox.admin.common.security.SecurityUtils;
import io.github.sandbox.admin.rbac.entity.AdminUser;
import io.github.sandbox.admin.rbac.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * ApiKey 管理服务（T-0029，FR-APIKEY-01~08；design.md §6.2、§11.2）。
 *
 * <p>安全硬约束（默认决策 #1）：明文只在 {@link #create} / {@link #regenerate}
 * 返回瞬间存在；数据库仅持久化 SHA-256 摘要 + 前缀 + 后四位掩码；
 * 任何列表、详情响应使用 {@link ApiKeyVO}（无明文字段）。</p>
 *
 * <p>状态机（design.md §6.2）：启用 ↔ 停用；启用/停用 → 已撤销（不可逆）；
 * 超过过期时间 → 已过期（查询时惰性判定并回写）。
 * 重新生成 = 撤销旧密钥 + 以相同元数据签发全新密钥（旧凭证即刻失效）。
 * 所有写操作经 Controller 的 @OperationLog 进入 admin_op_log（FR-APIKEY-08）。</p>
 *
 * <p>数据权限：client_api_key 已注册 SELF 行过滤（T-0021），
 * 普通用户仅能操作/查询"绑定用户=自己，或绑定为空且所属客户端归属自己"的密钥。</p>
 */
@Service
@RequiredArgsConstructor
public class ApiKeyAppService {

    private final ClientApiKeyMapper apiKeyMapper;
    private final ClientAppService clientAppService;
    private final AdminUserMapper adminUserMapper;

    /** 分页 + 筛选（FR-APIKEY-07），富化客户端与绑定用户展示字段 */
    public PageResult<ApiKeyVO> page(ApiKeyQuery query) {
        LambdaQueryWrapper<ClientApiKey> wrapper = Wrappers.<ClientApiKey>lambdaQuery()
                .like(StringUtils.hasText(query.getName()), ClientApiKey::getName, query.getName())
                .eq(query.getClientId() != null, ClientApiKey::getClientId, query.getClientId())
                .eq(query.getBoundUserId() != null, ClientApiKey::getBoundUserId, query.getBoundUserId())
                .eq(query.getStatus() != null, ClientApiKey::getStatus, query.getStatus())
                .ge(query.getExpireBegin() != null, ClientApiKey::getExpireTime, query.getExpireBegin())
                .le(query.getExpireEnd() != null, ClientApiKey::getExpireTime, query.getExpireEnd());
        applyOrder(wrapper, query);
        Page<ClientApiKey> page = apiKeyMapper.selectPage(
                new Page<>(Math.max(1, query.getPageNum()), clampSize(query.getPageSize())), wrapper);
        List<ApiKeyVO> vos = enrich(page.getRecords());
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 详情（经数据权限行过滤，越权即不存在；不含明文） */
    public ApiKeyVO detail(Long id) {
        return enrichOne(requireVisible(id));
    }

    /** 创建（FR-APIKEY-01/02）：生成明文，仅落库摘要/前缀/掩码，响应一次性携带明文 */
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyCreateVO create(ApiKeyUpsertRequest request) {
        validateTimes(request);
        ClientApp client = clientAppService.requireVisibleClient(request.getClientId());
        Long boundUserId = resolveBoundUserForWrite(request.getBoundUserId());

        ApiKeyGenerator.Generated generated = ApiKeyGenerator.generate();
        ClientApiKey entity = new ClientApiKey();
        entity.setName(request.getName());
        entity.setClientId(client.getId());
        entity.setBoundUserId(boundUserId);
        entity.setKeyHash(generated.getKeyHash());
        entity.setKeyPrefix(generated.getKeyPrefix());
        entity.setKeySuffixMask(generated.getKeySuffixMask());
        entity.setEffectiveTime(request.getEffectiveTime());
        entity.setExpireTime(request.getExpireTime());
        entity.setStatus(ClientApiKey.STATUS_ENABLED);
        entity.setRateLimitExempt(request.getRateLimitExempt() == null ? 0 : request.getRateLimitExempt());
        entity.setPlaintextOneShot(1);
        entity.setRemark(request.getRemark());
        apiKeyMapper.insert(entity);

        ApiKeyCreateVO vo = new ApiKeyCreateVO();
        vo.setApiKey(enrichOne(entity));
        vo.setPlaintext(generated.getPlaintext());
        vo.setNotice("明文仅此一次展示，请立即复制保存；关闭后将无法再次查看，只能重新生成。");
        return vo;
    }

    /** 编辑元数据（名称/备注/生效过期/白名单标志；不涉及密钥材料替换） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ApiKeyUpsertRequest request) {
        ClientApiKey existing = requireVisible(id);
        if (existing.getStatus() == ClientApiKey.STATUS_REVOKED) {
            throw new BusinessException(ErrorCode.API_KEY_STATE_CONFLICT, "已撤销的 ApiKey 不可编辑");
        }
        validateTimes(request);
        Long boundUserId = resolveBoundUserForWrite(request.getBoundUserId());

        ClientApiKey update = new ClientApiKey();
        update.setId(id);
        update.setName(request.getName());
        update.setEffectiveTime(request.getEffectiveTime());
        update.setExpireTime(request.getExpireTime());
        update.setRateLimitExempt(request.getRateLimitExempt());
        update.setRemark(request.getRemark());
        apiKeyMapper.updateById(update);
        // boundUserId 允许显式清空（updateById 忽略 null）；普通用户解析结果恒为自身，不会清空
        if (request.getBoundUserId() == null && existing.getBoundUserId() != null
                && SecurityUtils.isAllScope()) {
            apiKeyMapper.update(null, Wrappers.<ClientApiKey>lambdaUpdate()
                    .eq(ClientApiKey::getId, id)
                    .set(ClientApiKey::getBoundUserId, null));
        }
    }

    /** 启停用（FR-APIKEY-03；已撤销/已过期不可再启用） */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null
                || (status != ClientApiKey.STATUS_ENABLED && status != ClientApiKey.STATUS_DISABLED)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态仅支持 1=启用 2=停用");
        }
        ClientApiKey existing = requireVisible(id);
        if (existing.getStatus() == ClientApiKey.STATUS_REVOKED) {
            throw new BusinessException(ErrorCode.API_KEY_STATE_CONFLICT, "已撤销的 ApiKey 不可变更状态（撤销不可逆）");
        }
        if (status == ClientApiKey.STATUS_ENABLED
                && existing.getStatus() == ClientApiKey.STATUS_EXPIRED) {
            throw new BusinessException(ErrorCode.API_KEY_STATE_CONFLICT, "已过期的 ApiKey 不可重新启用，请重新生成");
        }
        ClientApiKey update = new ClientApiKey();
        update.setId(id);
        update.setStatus(status);
        apiKeyMapper.updateById(update);
    }

    /** 撤销（FR-APIKEY-04）：即刻失效且不可逆，同时消费一次性明文标记 */
    @Transactional(rollbackFor = Exception.class)
    public void revoke(Long id) {
        ClientApiKey existing = requireVisible(id);
        if (existing.getStatus() == ClientApiKey.STATUS_REVOKED) {
            return; // 幂等：重复撤销不报错、不重复留痕语义差异
        }
        ClientApiKey update = new ClientApiKey();
        update.setId(id);
        update.setStatus(ClientApiKey.STATUS_REVOKED);
        update.setPlaintextOneShot(0);
        apiKeyMapper.updateById(update);
    }

    /** 逻辑删除（管理端清理用途；已撤销/已过期才允许删除，避免误删在用凭证） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        ClientApiKey existing = requireVisible(id);
        if (existing.getStatus() == ClientApiKey.STATUS_ENABLED
                || existing.getStatus() == ClientApiKey.STATUS_DISABLED) {
            throw new BusinessException(ErrorCode.API_KEY_STATE_CONFLICT,
                    "仅已撤销或已过期的 ApiKey 可删除，请先撤销");
        }
        apiKeyMapper.deleteById(id);
    }

    /**
     * 重新生成（FR-APIKEY-06，design.md §6.2）：
     * 撤销旧密钥（旧明文即刻失效）+ 以相同归属/名称/备注签发全新密钥，
     * 响应一次性携带新明文。
     */
    @Transactional(rollbackFor = Exception.class)
    public ApiKeyCreateVO regenerate(Long id) {
        ClientApiKey existing = requireVisible(id);
        if (existing.getStatus() == ClientApiKey.STATUS_REVOKED) {
            throw new BusinessException(ErrorCode.API_KEY_STATE_CONFLICT,
                    "已撤销的 ApiKey 不能重新生成（请重新创建）");
        }
        revoke(id);

        ApiKeyGenerator.Generated generated = ApiKeyGenerator.generate();
        ClientApiKey entity = new ClientApiKey();
        entity.setName(existing.getName());
        entity.setClientId(existing.getClientId());
        entity.setBoundUserId(existing.getBoundUserId());
        entity.setKeyHash(generated.getKeyHash());
        entity.setKeyPrefix(generated.getKeyPrefix());
        entity.setKeySuffixMask(generated.getKeySuffixMask());
        entity.setEffectiveTime(null); // 立即生效
        entity.setExpireTime(existing.getExpireTime());
        entity.setStatus(ClientApiKey.STATUS_ENABLED);
        entity.setRateLimitExempt(existing.getRateLimitExempt());
        entity.setPlaintextOneShot(1);
        entity.setRemark(existing.getRemark());
        apiKeyMapper.insert(entity);

        ApiKeyCreateVO vo = new ApiKeyCreateVO();
        vo.setApiKey(enrichOne(entity));
        vo.setPlaintext(generated.getPlaintext());
        vo.setNotice("旧密钥已即刻失效；新明文仅此一次展示，请立即复制保存。");
        return vo;
    }

    /**
     * 惰性过期判定：对"当前用户可见且状态=启用/停用但 expireTime 已过"的密钥回写为已过期。
     * 列表与详情查询前调用，保证界面状态与 python-sandbox 拒绝语义一致（FR-APIKEY-05）。
     */
    public void lazilyExpire() {
        List<ClientApiKey> stale = apiKeyMapper.selectList(Wrappers.<ClientApiKey>lambdaQuery()
                .isNotNull(ClientApiKey::getExpireTime)
                .lt(ClientApiKey::getExpireTime, LocalDateTime.now())
                .in(ClientApiKey::getStatus, ClientApiKey.STATUS_ENABLED, ClientApiKey.STATUS_DISABLED));
        for (ClientApiKey key : stale) {
            ClientApiKey update = new ClientApiKey();
            update.setId(key.getId());
            update.setStatus(ClientApiKey.STATUS_EXPIRED);
            apiKeyMapper.updateById(update);
        }
    }

    /** 供限流模块校验目标 ApiKey 存在且在当前用户可见域内，并返回实体 */
    public ClientApiKey requireVisibleApiKey(Long apiKeyId) {
        return requireVisible(apiKeyId);
    }

    // ===================== internal =====================

    private ClientApiKey requireVisible(Long id) {
        ClientApiKey key = apiKeyMapper.selectById(id);
        if (key == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "ApiKey 不存在或无权访问");
        }
        return key;
    }

    private void validateTimes(ApiKeyUpsertRequest request) {
        if (request.getEffectiveTime() != null && request.getExpireTime() != null
                && !request.getEffectiveTime().isBefore(request.getExpireTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生效时间必须早于过期时间");
        }
    }

    /**
     * 绑定用户写入口径：普通用户仅可留空或绑定自身；ALL 域可绑定任意存在的用户或留空。
     */
    private Long resolveBoundUserForWrite(Long requestedBound) {
        AdminLoginUser me = SecurityUtils.getLoginUser();
        if (requestedBound == null) {
            return null;
        }
        if (!me.isAllScope() && !requestedBound.equals(me.getUserId())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "普通用户只能将 ApiKey 绑定给自己");
        }
        AdminUser user = adminUserMapper.selectById(requestedBound);
        if (user == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "绑定用户不存在");
        }
        return requestedBound;
    }

    private List<ApiKeyVO> enrich(List<ClientApiKey> keys) {
        if (keys.isEmpty()) {
            return List.of();
        }
        Map<Long, ClientApp> clientMap = keys.stream()
                .map(ClientApiKey::getClientId).distinct()
                .map(clientAppService::requireVisibleClient)
                .collect(Collectors.toMap(ClientApp::getId, Function.identity()));
        List<Long> userIds = keys.stream().map(ClientApiKey::getBoundUserId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, AdminUser> userMap = userIds.isEmpty() ? Map.of()
                : adminUserMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(AdminUser::getId, Function.identity()));
        return keys.stream().map(key -> toVO(key, clientMap, userMap)).toList();
    }

    private ApiKeyVO enrichOne(ClientApiKey key) {
        ClientApp client = clientAppService.requireVisibleClient(key.getClientId());
        Map<Long, ClientApp> clientMap = Map.of(client.getId(), client);
        Map<Long, AdminUser> userMap = Map.of();
        if (key.getBoundUserId() != null) {
            AdminUser user = adminUserMapper.selectById(key.getBoundUserId());
            if (user != null) {
                userMap = Map.of(user.getId(), user);
            }
        }
        return toVO(key, clientMap, userMap);
    }

    private ApiKeyVO toVO(ClientApiKey key, Map<Long, ClientApp> clientMap, Map<Long, AdminUser> userMap) {
        ApiKeyVO vo = new ApiKeyVO();
        vo.setId(key.getId());
        vo.setName(key.getName());
        vo.setClientId(key.getClientId());
        ClientApp client = clientMap.get(key.getClientId());
        if (client != null) {
            vo.setClientCode(client.getClientCode());
            vo.setClientName(client.getClientName());
        }
        vo.setBoundUserId(key.getBoundUserId());
        AdminUser user = key.getBoundUserId() == null ? null : userMap.get(key.getBoundUserId());
        if (user != null) {
            vo.setBoundUserName(user.getUsername());
        }
        vo.setKeyPrefix(key.getKeyPrefix());
        vo.setKeySuffixMask(key.getKeySuffixMask());
        vo.setEffectiveTime(key.getEffectiveTime());
        vo.setExpireTime(key.getExpireTime());
        vo.setStatus(key.getStatus());
        vo.setStatusLabel(statusLabel(key));
        vo.setRateLimitExempt(key.getRateLimitExempt());
        vo.setPlaintextOneShot(key.getPlaintextOneShot());
        vo.setRemark(key.getRemark());
        vo.setCreateTime(key.getCreateTime());
        vo.setUpdateTime(key.getUpdateTime());
        return vo;
    }

    /** 状态标签：在持久状态基础上合成"未生效/已过期"实时语义（仅展示，不回写） */
    private String statusLabel(ClientApiKey key) {
        LocalDateTime now = LocalDateTime.now();
        if (key.getStatus() == ClientApiKey.STATUS_ENABLED
                && key.getEffectiveTime() != null && key.getEffectiveTime().isAfter(now)) {
            return "未生效";
        }
        if (key.getStatus() == ClientApiKey.STATUS_ENABLED
                && key.getExpireTime() != null && key.getExpireTime().isBefore(now)) {
            return "已过期";
        }
        return switch (key.getStatus()) {
            case ClientApiKey.STATUS_ENABLED -> "启用";
            case ClientApiKey.STATUS_DISABLED -> "停用";
            case ClientApiKey.STATUS_EXPIRED -> "已过期";
            case ClientApiKey.STATUS_REVOKED -> "已撤销";
            default -> "未知";
        };
    }

    private void applyOrder(LambdaQueryWrapper<ClientApiKey> wrapper, ApiKeyQuery query) {
        boolean asc = Boolean.TRUE.equals(query.getAsc());
        String orderBy = query.getOrderBy() == null ? "createTime" : query.getOrderBy();
        switch (orderBy) {
            case "name" -> wrapper.orderBy(true, asc, ClientApiKey::getName);
            case "expireTime" -> wrapper.orderBy(true, asc, ClientApiKey::getExpireTime);
            case "status" -> wrapper.orderBy(true, asc, ClientApiKey::getStatus);
            case "id" -> wrapper.orderBy(true, asc, ClientApiKey::getId);
            default -> wrapper.orderBy(true, asc, ClientApiKey::getCreateTime);
        }
    }

    private long clampSize(long size) {
        return Math.min(Math.max(1, size), 200);
    }
}
