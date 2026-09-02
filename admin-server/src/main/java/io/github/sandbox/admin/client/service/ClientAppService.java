package io.github.sandbox.admin.client.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.sandbox.admin.apikey.entity.ClientApiKey;
import io.github.sandbox.admin.apikey.mapper.ClientApiKeyMapper;
import io.github.sandbox.admin.client.dto.ClientQuery;
import io.github.sandbox.admin.client.dto.ClientStatsVO;
import io.github.sandbox.admin.client.dto.ClientUpsertRequest;
import io.github.sandbox.admin.client.entity.ClientApp;
import io.github.sandbox.admin.client.mapper.ClientAppMapper;
import io.github.sandbox.admin.common.datapermission.DataPermissionIgnoreHolder;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.security.AdminLoginUser;
import io.github.sandbox.admin.common.security.SecurityUtils;
import io.github.sandbox.admin.log.entity.ApiLogView;
import io.github.sandbox.admin.log.entity.SandboxOperationLogView;
import io.github.sandbox.admin.log.mapper.ApiLogViewMapper;
import io.github.sandbox.admin.log.mapper.SandboxOperationLogViewMapper;
import io.github.sandbox.admin.rbac.entity.AdminUser;
import io.github.sandbox.admin.rbac.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户端管理服务（T-0028，FR-CLIENT-01~04；design.md §6.1、§10.3）。
 *
 * <p>数据权限：client_app 已注册进 {@code AdminDataPermissionHandler} SELF 行过滤，
 * 普通用户的列表/编辑/启停/删除天然限定为"归属用户=自己"；越权记录查询不到即拒绝（FR-RBAC-04）。</p>
 *
 * <p>归属口径：普通用户新增客户端时 ownerUserId 强制为自身（防止把自己数据挂到他人名下）；
 * 管理员可指定归属用户或留空（默认决策 #4：允许为空=按客户端维度计，管理员背书）。</p>
 *
 * <p>删除阻断（FR-CLIENT-04）：仍持有"未撤销且未过期"ApiKey 的客户端禁止删除，
 * 提示先处理（撤销/删除）名下密钥；停用客户端（status=0）后，python-sandbox 侧
 * ApiKey 校验（T-0023 查 client_app.status）即刻形成 CLIENT_DISABLED 拒绝语义。</p>
 */
@Service
@RequiredArgsConstructor
public class ClientAppService {

    private final ClientAppMapper clientAppMapper;
    private final ClientApiKeyMapper clientApiKeyMapper;
    private final AdminUserMapper adminUserMapper;
    private final ApiLogViewMapper apiLogViewMapper;
    private final SandboxOperationLogViewMapper sandboxOperationLogViewMapper;

    /** 分页 + 筛选（FR-CLIENT-01） */
    public PageResult<ClientApp> page(ClientQuery query) {
        LambdaQueryWrapper<ClientApp> wrapper = Wrappers.<ClientApp>lambdaQuery()
                .like(StringUtils.hasText(query.getClientName()), ClientApp::getClientName, query.getClientName())
                .like(StringUtils.hasText(query.getClientCode()), ClientApp::getClientCode, query.getClientCode())
                .eq(query.getOwnerUserId() != null, ClientApp::getOwnerUserId, query.getOwnerUserId())
                .eq(query.getStatus() != null, ClientApp::getStatus, query.getStatus());
        applyOrder(wrapper, query);
        Page<ClientApp> page = clientAppMapper.selectPage(
                new Page<>(Math.max(1, query.getPageNum()), clampSize(query.getPageSize())), wrapper);
        return PageResult.of(page);
    }

    /** 详情（经数据权限行过滤，越权即不存在） */
    public ClientApp detail(Long id) {
        ClientApp app = clientAppMapper.selectById(id);
        if (app == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "客户端不存在或无权访问");
        }
        return app;
    }

    /** 新增（FR-CLIENT-02：编码唯一） */
    @Transactional(rollbackFor = Exception.class)
    public Long create(ClientUpsertRequest request) {
        AdminLoginUser me = SecurityUtils.getLoginUser();
        if (existsCode(request.getClientCode(), null)) {
            throw new BusinessException(ErrorCode.CLIENT_CODE_EXISTS);
        }
        Long ownerUserId = resolveOwnerForWrite(request.getOwnerUserId(), me);

        ClientApp app = new ClientApp();
        app.setClientCode(request.getClientCode());
        app.setClientName(request.getClientName());
        app.setDescription(request.getDescription());
        app.setOwnerUserId(ownerUserId);
        app.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        app.setRemark(request.getRemark());
        clientAppMapper.insert(app);
        return app.getId();
    }

    /** 编辑（不含状态；编码唯一校验） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ClientUpsertRequest request) {
        ClientApp existing = detail(id); // 越权/不存在统一拒绝
        if (existsCode(request.getClientCode(), id)) {
            throw new BusinessException(ErrorCode.CLIENT_CODE_EXISTS);
        }
        AdminLoginUser me = SecurityUtils.getLoginUser();
        ClientApp update = new ClientApp();
        update.setId(id);
        update.setClientCode(request.getClientCode());
        update.setClientName(request.getClientName());
        update.setDescription(request.getDescription());
        update.setOwnerUserId(resolveOwnerForWrite(request.getOwnerUserId(), me));
        update.setRemark(request.getRemark());
        clientAppMapper.updateById(update);
        // 归属用户可被显式清空（ALL 域语义）；updateById 忽略 null 字段，需显式改写。
        // 普通用户 resolveOwnerForWrite 恒返回自身，不会进入清空分支。
        if (me.isAllScope() && request.getOwnerUserId() == null
                && existing.getOwnerUserId() != null) {
            clientAppMapper.update(null, Wrappers.<ClientApp>lambdaUpdate()
                    .eq(ClientApp::getId, id)
                    .set(ClientApp::getOwnerUserId, null));
        }
        // 状态变更不经编辑接口（保证"启停"审计语义单一入口），此处仅更新基础字段。
    }

    /** 启停用（FR-CLIENT-03：停用即刻使 python-sandbox 端拒绝其名下启用 ApiKey） */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态仅支持 0=停用 1=启用");
        }
        detail(id); // 数据权限行过滤 + 存在性校验
        ClientApp update = new ClientApp();
        update.setId(id);
        update.setStatus(status);
        clientAppMapper.updateById(update);
    }

    /**
     * 删除（FR-CLIENT-04）：仍持有"启用/停用（未撤销未过期）"ApiKey 的客户端阻断删除。
     *
     * <p>逻辑删除后 client_app 行不再可见；其名下"已撤销/已过期"的历史 ApiKey 保留，
     * 历史调用记录仍可按归属查询（不悬空）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        detail(id);
        long activeKeys = clientApiKeyMapper.selectCount(Wrappers.<ClientApiKey>lambdaQuery()
                .eq(ClientApiKey::getClientId, id)
                .in(ClientApiKey::getStatus, ClientApiKey.STATUS_ENABLED, ClientApiKey.STATUS_DISABLED));
        if (activeKeys > 0) {
            throw new BusinessException(ErrorCode.CLIENT_HAS_ACTIVE_KEYS,
                    "该客户端仍持有 " + activeKeys + " 个有效 ApiKey，请先撤销或删除后再删除客户端");
        }
        clientAppMapper.deleteById(id);
    }

    /** 供 ApiKey 模块校验目标客户端存在且在当前用户可见域内 */
    public ClientApp requireVisibleClient(Long clientId) {
        ClientApp app = clientAppMapper.selectById(clientId);
        if (app == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "客户端不存在或无权访问");
        }
        return app;
    }

    /** 跨可见域统计 ApiKey 数等（仅供 ALL 域聚合使用；调用方负责权限） */
    public long countKeysOf(Long clientId) {
        java.util.function.Supplier<Long> query = () -> clientApiKeyMapper.selectCount(
                Wrappers.<ClientApiKey>lambdaQuery().eq(ClientApiKey::getClientId, clientId));
        return DataPermissionIgnoreHolder.runIgnored(query);
    }

    /**
     * 客户端统计卡片（T-0035，FR-CLIENT-05）：ApiKey 总数 / 活跃数 / 今日调用 / 累计调用。
     *
     * <p>先经 detail() 做可见域校验（普通用户只能统计自己的客户端）；
     * 聚合查询以 runIgnored 绕过行过滤（目标已确认在可见域内，且 api_log 的
     * owner_user_id 是历史归属快照，不能以"当前查看者"再过滤一次——
     * 统计口径以 client_id 为准，与当前客户端筛选范围一致，验收口径）。</p>
     */
    public ClientStatsVO stats(Long id) {
        detail(id); // 越权/不存在统一拒绝（不透出他方数据）
        LocalDateTime now = LocalDateTime.now();
        ClientStatsVO vo = new ClientStatsVO();
        DataPermissionIgnoreHolder.runIgnored(() -> {
            vo.setApiKeyCount(clientApiKeyMapper.selectCount(Wrappers.<ClientApiKey>lambdaQuery()
                    .eq(ClientApiKey::getClientId, id)));
            vo.setActiveApiKeyCount(clientApiKeyMapper.selectCount(Wrappers.<ClientApiKey>lambdaQuery()
                    .eq(ClientApiKey::getClientId, id)
                    .in(ClientApiKey::getStatus, ClientApiKey.STATUS_ENABLED, ClientApiKey.STATUS_DISABLED)
                    .and(w -> w.isNull(ClientApiKey::getExpireTime).or().gt(ClientApiKey::getExpireTime, now))));
            vo.setTodayCalls(apiLogViewMapper.selectCount(Wrappers.<ApiLogView>lambdaQuery()
                    .eq(ApiLogView::getClientId, id)
                    .ge(ApiLogView::getCreatedAt, LocalDate.now().atStartOfDay())));
            vo.setTotalCalls(apiLogViewMapper.selectCount(Wrappers.<ApiLogView>lambdaQuery()
                    .eq(ApiLogView::getClientId, id)));
        });
        return vo;
    }

    /**
     * 归属转移（T-0035，FR-CLIENT-06）：客户端归属从 A 转移到 B，
     * 历史调用记录归属同步更新（api_log / sandbox_operation_log 中该客户端名下
     * 仍归属旧主人的记录快照改写为新主人），保证"转移后按新归属用户展示"（验收口径）。
     *
     * <p>权限口径：普通用户仅能转移自己可见域内的客户端（detail() 行过滤），
     * 且仅允许"转移到自己"（把自己客户端的归属交给管理员清空不在此列）或
     * "从自己转出给指定用户"需 ALL 域；ALL 域可任意转移。
     * 越权转移在 detail()/校验处即拒绝，Controller 的 @OperationLog 切面
     * 仅记录成功动作，失败由业务异常透出（审计的越权事件由统一异常路径表达）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void transferOwner(Long id, Long targetOwnerId) {
        ClientApp existing = detail(id);
        if (targetOwnerId == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "必须指定目标归属用户");
        }
        AdminLoginUser me = SecurityUtils.getLoginUser();
        if (!me.isAllScope()) {
            // 普通用户：只能转移自己名下客户端；目标为自己时无意义（直接拒绝空转），
            // 转出给他人同样不允许（防止把历史责任甩给他人），仅 ALL 域可执行转移。
            throw new BusinessException(ErrorCode.NO_PERMISSION, "客户端归属转移仅管理员可执行");
        }
        AdminUser target = adminUserMapper.selectById(targetOwnerId);
        if (target == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标归属用户不存在");
        }
        if (java.util.Objects.equals(existing.getOwnerUserId(), targetOwnerId)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "目标归属用户与当前归属相同");
        }
        clientAppMapper.update(null, Wrappers.<ClientApp>lambdaUpdate()
                .eq(ClientApp::getId, id)
                .set(ClientApp::getOwnerUserId, targetOwnerId));
        // 历史调用记录归属同步：该客户端下"归属=旧主人（或无归属）"的日志快照改到新主人，
        // 已显式绑定其他用户的 ApiKey 日志（api_log.owner_user_id 非旧主人）不受影响。
        DataPermissionIgnoreHolder.runIgnored(() -> {
            apiLogViewMapper.update(null, Wrappers.<ApiLogView>lambdaUpdate()
                    .eq(ApiLogView::getClientId, id)
                    .and(w -> w.eq(ApiLogView::getOwnerUserId, existing.getOwnerUserId() == null ? -1L : existing.getOwnerUserId())
                            .or().isNull(ApiLogView::getOwnerUserId))
                    .set(ApiLogView::getOwnerUserId, targetOwnerId));
            sandboxOperationLogViewMapper.update(null, Wrappers.<SandboxOperationLogView>lambdaUpdate()
                    .eq(SandboxOperationLogView::getClientId, id)
                    .and(w -> w.eq(SandboxOperationLogView::getOwnerUserId, existing.getOwnerUserId() == null ? -1L : existing.getOwnerUserId())
                            .or().isNull(SandboxOperationLogView::getOwnerUserId))
                    .set(SandboxOperationLogView::getOwnerUserId, targetOwnerId));
        });
    }

    // ===================== internal =====================

    /**
     * 归属用户写入口径：
     * 普通用户强制自身；ALL 域可指定任意存在的用户或留空。
     */
    private Long resolveOwnerForWrite(Long requestedOwner, AdminLoginUser me) {
        if (!me.isAllScope()) {
            return me.getUserId();
        }
        if (requestedOwner != null) {
            AdminUser owner = adminUserMapper.selectById(requestedOwner);
            if (owner == null) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "归属用户不存在");
            }
        }
        return requestedOwner;
    }

    private boolean existsCode(String code, Long excludeId) {
        // 编码唯一为全局约束（schema uk_client_app_code），跨可见域校验需忽略 SELF 行过滤，
        // 冲突时仅返回"编码已存在"语义、不透出他方客户端数据（FR-RBAC-04）。
        return DataPermissionIgnoreHolder.runIgnored(() -> clientAppMapper.selectCount(
                Wrappers.<ClientApp>lambdaQuery()
                        .eq(ClientApp::getClientCode, code)
                        .ne(excludeId != null, ClientApp::getId, excludeId)) > 0);
    }

    private void applyOrder(LambdaQueryWrapper<ClientApp> wrapper, ClientQuery query) {
        boolean asc = Boolean.TRUE.equals(query.getAsc());
        String orderBy = query.getOrderBy() == null ? "createTime" : query.getOrderBy();
        switch (orderBy) {
            case "clientCode" -> wrapper.orderBy(true, asc, ClientApp::getClientCode);
            case "clientName" -> wrapper.orderBy(true, asc, ClientApp::getClientName);
            case "id" -> wrapper.orderBy(true, asc, ClientApp::getId);
            default -> wrapper.orderBy(true, asc, ClientApp::getCreateTime);
        }
    }

    private long clampSize(long size) {
        return Math.min(Math.max(1, size), 200);
    }

    /** 列表便捷访问（当前用户可见域内全部客户端 id，用于限流目标校验等聚合场景） */
    public List<Long> visibleClientIdsSelfScope() {
        return clientAppMapper.selectList(Wrappers.<ClientApp>lambdaQuery()
                        .select(ClientApp::getId))
                .stream().map(ClientApp::getId).toList();
    }
}
