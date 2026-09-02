package io.github.sandbox.admin.apikey.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.github.sandbox.admin.apikey.dto.ApiKeyCreateVO;
import io.github.sandbox.admin.apikey.dto.ApiKeyQuery;
import io.github.sandbox.admin.apikey.dto.ApiKeyUpsertRequest;
import io.github.sandbox.admin.apikey.dto.ApiKeyVO;
import io.github.sandbox.admin.apikey.service.ApiKeyAppService;
import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * ApiKey 管理接口（T-0029 后端部分，design.md §10.3：/admin-api/apikeys，§11.2）。
 *
 * <p>明文一次性展示（默认决策 #1）：仅 POST / 与 POST /{id}/regenerate 的响应
 * 携带一次 plaintext；列表与详情永不含明文/摘要。
 * 创建、启停、撤销、重新生成均经 @OperationLog 进入 admin_op_log（FR-APIKEY-08）。</p>
 */
@RestController
@RequestMapping("/apikeys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyAppService apiKeyAppService;

    /** 分页列表 + 客户端/用户/状态/过期时间范围筛选（FR-APIKEY-07；含惰性过期回写） */
    @SaCheckPermission("apikey:view")
    @GetMapping
    public R<PageResult<ApiKeyVO>> page(ApiKeyQuery query) {
        apiKeyAppService.lazilyExpire();
        return R.ok(apiKeyAppService.page(query));
    }

    /** 详情（仅前缀+掩码识别，无明文） */
    @SaCheckPermission("apikey:view")
    @GetMapping("/{id}")
    public R<ApiKeyVO> detail(@PathVariable Long id) {
        return R.ok(apiKeyAppService.detail(id));
    }

    /** 创建：响应一次性携带明文（FR-APIKEY-01/02） */
    @SaCheckPermission("apikey:add")
    @OperationLog(module = "apikey", type = "add")
    @PostMapping
    public R<ApiKeyCreateVO> create(@Valid @RequestBody ApiKeyUpsertRequest request) {
        return R.ok(apiKeyAppService.create(request));
    }

    /** 编辑元数据（名称/时间/白名单/备注，不触碰密钥材料） */
    @SaCheckPermission("apikey:edit")
    @OperationLog(module = "apikey", type = "edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ApiKeyUpsertRequest request) {
        apiKeyAppService.update(id, request);
        return R.ok();
    }

    /** 启停用（FR-APIKEY-03） */
    @SaCheckPermission("apikey:disable")
    @OperationLog(module = "apikey", type = "disable")
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        apiKeyAppService.changeStatus(id, status);
        return R.ok();
    }

    /** 撤销（FR-APIKEY-04：不可逆） */
    @SaCheckPermission("apikey:revoke")
    @OperationLog(module = "apikey", type = "revoke")
    @PutMapping("/{id}/revoke")
    public R<Void> revoke(@PathVariable Long id) {
        apiKeyAppService.revoke(id);
        return R.ok();
    }

    /** 重新生成（FR-APIKEY-06：旧密钥即刻撤销，新明文一次性返回） */
    @SaCheckPermission("apikey:reset")
    @OperationLog(module = "apikey", type = "reset")
    @PostMapping("/{id}/regenerate")
    public R<ApiKeyCreateVO> regenerate(@PathVariable Long id) {
        return R.ok(apiKeyAppService.regenerate(id));
    }

    // 注：本模块不开放物理/逻辑删除接口——种子权限码仅登记
    // apikey:view/add/edit/disable/revoke/reset（seed/001 §4），
    // "删除前处理"语义由撤销（不可逆）承载，客户端删除前由 FR-CLIENT-04 阻断校验兜底。
}
