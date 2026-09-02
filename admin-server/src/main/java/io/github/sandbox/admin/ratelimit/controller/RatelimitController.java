package io.github.sandbox.admin.ratelimit.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.bridge.client.SandboxBridgeClient;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.result.R;
import io.github.sandbox.admin.ratelimit.dto.RatelimitQuery;
import io.github.sandbox.admin.ratelimit.dto.RatelimitUpsertRequest;
import io.github.sandbox.admin.ratelimit.entity.RatelimitRule;
import io.github.sandbox.admin.ratelimit.service.RatelimitRuleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 限流规则管理接口（T-0030 后端部分，design.md §10.3：/admin-api/ratelimits）。
 *
 * <p>规则写操作成功后由 Service 触发 python-sandbox 立即刷新；本 Controller 另提供
 * 管理员手动"刷新规则"入口（对应内部接口 POST /internal/sandbox/ratelimit/reload），
 * 刷新动作以 module=bridge 进入管理端审计（FR-RATELIMIT 验收：规则保存与刷新动作均有审计）。</p>
 */
@RestController
@RequestMapping("/ratelimits")
@RequiredArgsConstructor
public class RatelimitController {

    private final RatelimitRuleService ratelimitRuleService;
    private final SandboxBridgeClient sandboxBridgeClient;

    /** 分页列表 + 维度/目标/窗口/状态筛选 */
    @SaCheckPermission("ratelimit:view")
    @GetMapping
    public R<PageResult<RatelimitRule>> page(RatelimitQuery query) {
        return R.ok(ratelimitRuleService.page(query));
    }

    /** 详情 */
    @SaCheckPermission("ratelimit:view")
    @GetMapping("/{id}")
    public R<RatelimitRule> detail(@PathVariable Long id) {
        return R.ok(ratelimitRuleService.detail(id));
    }

    /** 新增（维度与目标一致性、目标存在性、阈值合法性在 Service 校验） */
    @SaCheckPermission("ratelimit:add")
    @OperationLog(module = "ratelimit", type = "add")
    @PostMapping
    public R<Long> create(@Valid @RequestBody RatelimitUpsertRequest request) {
        return R.ok(ratelimitRuleService.create(request));
    }

    /** 编辑 */
    @SaCheckPermission("ratelimit:edit")
    @OperationLog(module = "ratelimit", type = "edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody RatelimitUpsertRequest request) {
        ratelimitRuleService.update(id, request);
        return R.ok();
    }

    /** 启停用 */
    @SaCheckPermission("ratelimit:disable")
    @OperationLog(module = "ratelimit", type = "disable")
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        ratelimitRuleService.changeStatus(id, status);
        return R.ok();
    }

    /** 删除 */
    @SaCheckPermission("ratelimit:delete")
    @OperationLog(module = "ratelimit", type = "delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        ratelimitRuleService.delete(id);
        return R.ok();
    }

    /** 管理员手动触发 python-sandbox 立即重拉规则（定时拉取的补充手段） */
    @SaCheckPermission("ratelimit:edit")
    @OperationLog(module = "bridge", type = "edit")
    @PostMapping("/reload")
    public R<Map<String, Boolean>> reload() {
        boolean success = sandboxBridgeClient.reloadRatelimitRules();
        return R.ok(Map.of("success", success));
    }
}
