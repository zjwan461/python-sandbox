package io.github.sandbox.admin.ratelimit.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.sandbox.admin.apikey.entity.ClientApiKey;
import io.github.sandbox.admin.apikey.mapper.ClientApiKeyMapper;
import io.github.sandbox.admin.bridge.client.SandboxBridgeClient;
import io.github.sandbox.admin.client.entity.ClientApp;
import io.github.sandbox.admin.client.mapper.ClientAppMapper;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.security.AdminLoginUser;
import io.github.sandbox.admin.common.security.SecurityUtils;
import io.github.sandbox.admin.ratelimit.dto.RatelimitQuery;
import io.github.sandbox.admin.ratelimit.dto.RatelimitUpsertRequest;
import io.github.sandbox.admin.ratelimit.entity.RatelimitRule;
import io.github.sandbox.admin.ratelimit.mapper.RatelimitRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 限流规则管理服务（T-0030，FR-RATELIMIT-01/02/03；design.md §7.1、§7.3、§7.4）。
 *
 * <p>ratelimit_rule 为系统级元数据表，不受 SELF 行过滤（T-0021 验收：元数据表不被误作用），
 * 因此归属约束在本 Service 层显式完成（FR-RBAC-02）：普通用户只能对"自己可见域内"的
 * ApiKey / 客户端配置规则（借由 client_api_key / client_app 的 selectById 天然行过滤判定）；
 * GLOBAL 维度规则仅 ALL 域（管理员）可维护。</p>
 *
 * <p>保存/启停/删除成功后触发 python-sandbox 立即拉取最新规则
 * （{@code POST /internal/sandbox/ratelimit/reload}，作为定时拉取的补充触发器，§7.4）。
 * 刷新失败不回滚业务落库——python-sandbox 侧定时拉取兜底，仅告警并透出提示。</p>
 *
 * <p>唯一约束口径与 schema/003 uk(dimension,target_id,window_type,threshold,deleted) 对齐：
 * 相同维度+目标+窗口+阈值的重复规则拒绝保存（30010）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatelimitRuleService {

    private static final Set<String> DIMENSIONS =
            Set.of(RatelimitRule.DIM_API_KEY, RatelimitRule.DIM_CLIENT, RatelimitRule.DIM_GLOBAL);
    private static final Set<String> WINDOWS =
            Set.of(RatelimitRule.WINDOW_MINUTE, RatelimitRule.WINDOW_HOUR, RatelimitRule.WINDOW_DAY);

    private final RatelimitRuleMapper ruleMapper;
    private final ClientAppMapper clientAppMapper;
    private final ClientApiKeyMapper apiKeyMapper;
    private final SandboxBridgeClient bridgeClient;

    /** 分页 + 筛选 */
    public PageResult<RatelimitRule> page(RatelimitQuery query) {
        LambdaQueryWrapper<RatelimitRule> wrapper = Wrappers.<RatelimitRule>lambdaQuery()
                .eq(StringUtils.hasText(query.getDimension()), RatelimitRule::getDimension, query.getDimension())
                .eq(query.getTargetId() != null, RatelimitRule::getTargetId, query.getTargetId())
                .eq(StringUtils.hasText(query.getWindowType()), RatelimitRule::getWindowType, query.getWindowType())
                .eq(query.getStatus() != null, RatelimitRule::getStatus, query.getStatus());
        applyOrder(wrapper, query);
        Page<RatelimitRule> page = ruleMapper.selectPage(
                new Page<>(Math.max(1, query.getPageNum()), clampSize(query.getPageSize())), wrapper);
        return PageResult.of(page);
    }

    /** 详情 */
    public RatelimitRule detail(Long id) {
        RatelimitRule rule = ruleMapper.selectById(id);
        if (rule == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "限流规则不存在");
        }
        return rule;
    }

    /** 新增（保存成功后触发 python-sandbox 刷新） */
    @Transactional(rollbackFor = Exception.class)
    public Long create(RatelimitUpsertRequest request) {
        validate(request, null);
        RatelimitRule rule = new RatelimitRule();
        applyRequest(rule, request);
        rule.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        ruleMapper.insert(rule);
        triggerReload();
        return rule.getId();
    }

    /** 编辑（保存成功后触发刷新） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, RatelimitUpsertRequest request) {
        RatelimitRule existing = detail(id);
        requireTargetPermission(existing.getDimension(), existing.getTargetId());
        validate(request, id);
        RatelimitRule rule = new RatelimitRule();
        rule.setId(id);
        applyRequest(rule, request);
        if (request.getStatus() != null) {
            rule.setStatus(request.getStatus());
        }
        ruleMapper.updateById(rule);
        triggerReload();
    }

    /** 启停用（保存成功后触发刷新；停用规则不再被 python-sandbox 拉取） */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态仅支持 0=停用 1=启用");
        }
        RatelimitRule existing = detail(id);
        requireTargetPermission(existing.getDimension(), existing.getTargetId());
        RatelimitRule update = new RatelimitRule();
        update.setId(id);
        update.setStatus(status);
        ruleMapper.updateById(update);
        triggerReload();
    }

    /** 删除（逻辑删除；成功后触发刷新） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        RatelimitRule existing = detail(id);
        requireTargetPermission(existing.getDimension(), existing.getTargetId());
        ruleMapper.deleteById(id);
        triggerReload();
    }

    // ===================== internal =====================

    private void validate(RatelimitUpsertRequest request, Long excludeId) {
        String dimension = request.getDimension() == null ? "" : request.getDimension().toUpperCase();
        String window = request.getWindowType() == null ? "" : request.getWindowType().toUpperCase();
        if (!DIMENSIONS.contains(dimension)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "维度仅支持 API_KEY / CLIENT / GLOBAL");
        }
        if (!WINDOWS.contains(window)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "窗口类型仅支持 MINUTE / HOUR / DAY");
        }
        request.setDimension(dimension);
        request.setWindowType(window);
        if (request.getThreshold() == null || request.getThreshold() <= 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "阈值必须为正整数");
        }
        if (RatelimitRule.DIM_GLOBAL.equals(dimension)) {
            request.setTargetId(0L); // GLOBAL 目标固定 0
        } else if (request.getTargetId() == null || request.getTargetId() <= 0) {
            throw new BusinessException(ErrorCode.RATELIMIT_TARGET_INVALID);
        }
        if (request.getEffectiveTime() != null && request.getExpireTime() != null
                && !request.getEffectiveTime().isBefore(request.getExpireTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生效时间必须早于失效时间");
        }
        // 目标存在性 + 归属权限（普通用户目标必须在自身可见域内）
        requireTargetPermission(dimension, request.getTargetId());

        long dup = ruleMapper.selectCount(Wrappers.<RatelimitRule>lambdaQuery()
                .eq(RatelimitRule::getDimension, dimension)
                .eq(RatelimitRule::getTargetId, request.getTargetId())
                .eq(RatelimitRule::getWindowType, window)
                .eq(RatelimitRule::getThreshold, request.getThreshold())
                .ne(excludeId != null, RatelimitRule::getId, excludeId));
        if (dup > 0) {
            throw new BusinessException(ErrorCode.RATELIMIT_RULE_CONFLICT);
        }
    }

    /**
     * 目标维度与归属权限校验：
     * API_KEY → client_api_key.selectById（SELF 行过滤下越权即查不到）；
     * CLIENT  → client_app.selectById（同上）；
     * GLOBAL  → 仅 ALL 数据域（管理员）。
     */
    private void requireTargetPermission(String dimension, Long targetId) {
        AdminLoginUser me = SecurityUtils.getLoginUser();
        switch (dimension) {
            case RatelimitRule.DIM_GLOBAL -> {
                if (!me.isAllScope()) {
                    throw new BusinessException(ErrorCode.NO_PERMISSION, "全局默认规则仅管理员可维护");
                }
            }
            case RatelimitRule.DIM_API_KEY -> {
                ClientApiKey key = apiKeyMapper.selectById(targetId);
                if (key == null) {
                    throw new BusinessException(ErrorCode.RATELIMIT_TARGET_INVALID);
                }
            }
            case RatelimitRule.DIM_CLIENT -> {
                ClientApp app = clientAppMapper.selectById(targetId);
                if (app == null) {
                    throw new BusinessException(ErrorCode.RATELIMIT_TARGET_INVALID);
                }
            }
            default -> throw new BusinessException(ErrorCode.PARAM_ERROR, "未知限流维度: " + dimension);
        }
    }

    private void applyRequest(RatelimitRule rule, RatelimitUpsertRequest request) {
        rule.setDimension(request.getDimension());
        rule.setTargetId(request.getTargetId());
        rule.setWindowType(request.getWindowType());
        rule.setThreshold(request.getThreshold());
        rule.setPriority(request.getPriority() == null ? 100 : request.getPriority());
        rule.setEffectiveTime(request.getEffectiveTime());
        rule.setExpireTime(request.getExpireTime());
        rule.setRemark(request.getRemark());
    }

    /** 通知 python-sandbox 立即重拉规则；失败仅告警（定时拉取兜底），不阻断业务 */
    private void triggerReload() {
        try {
            bridgeClient.reloadRatelimitRules();
        } catch (Exception e) {
            log.warn("触发 python-sandbox 限流规则刷新失败（将由其定时拉取兜底）：{}", e.getMessage());
        }
    }

    private void applyOrder(LambdaQueryWrapper<RatelimitRule> wrapper, RatelimitQuery query) {
        boolean asc = Boolean.TRUE.equals(query.getAsc());
        String orderBy = query.getOrderBy() == null ? "priority" : query.getOrderBy();
        switch (orderBy) {
            case "id" -> wrapper.orderBy(true, asc, RatelimitRule::getId);
            case "threshold" -> wrapper.orderBy(true, asc, RatelimitRule::getThreshold);
            case "createTime" -> wrapper.orderBy(true, asc, RatelimitRule::getCreateTime);
            default -> {
                // 默认按优先级升序 + 创建时间倒序（判定次序直观可读）
                wrapper.orderBy(true, asc, RatelimitRule::getPriority)
                        .orderByDesc(RatelimitRule::getCreateTime);
            }
        }
    }

    private long clampSize(long size) {
        return Math.min(Math.max(1, size), 200);
    }

    /** 便于测试/内部使用的当前时间口径 */
    protected LocalDateTime now() {
        return LocalDateTime.now();
    }
}
