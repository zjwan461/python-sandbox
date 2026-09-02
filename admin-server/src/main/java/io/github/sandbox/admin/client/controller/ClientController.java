package io.github.sandbox.admin.client.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.client.dto.ClientQuery;
import io.github.sandbox.admin.client.dto.ClientUpsertRequest;
import io.github.sandbox.admin.client.entity.ClientApp;
import io.github.sandbox.admin.client.service.ClientAppService;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 客户端管理接口（T-0028 后端部分，design.md §10.3：/admin-api/clients）。
 *
 * <p>按钮权限经 @SaCheckPermission 服务端校验；数据范围经 SELF 行过滤（T-0021），
 * 普通用户仅能操作归属自己的客户端（FR-CLIENT、FR-RBAC-02/04）。</p>
 */
@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientAppService clientAppService;

    /** 分页列表 + 名称/编码/归属人/状态筛选（FR-CLIENT-01） */
    @SaCheckPermission("client:view")
    @GetMapping
    public R<PageResult<ClientApp>> page(ClientQuery query) {
        return R.ok(clientAppService.page(query));
    }

    /** 详情 */
    @SaCheckPermission("client:view")
    @GetMapping("/{id}")
    public R<ClientApp> detail(@PathVariable Long id) {
        return R.ok(clientAppService.detail(id));
    }

    /** 新增（FR-CLIENT-02：编码唯一） */
    @SaCheckPermission("client:add")
    @OperationLog(module = "client", type = "add")
    @PostMapping
    public R<Long> create(@Valid @RequestBody ClientUpsertRequest request) {
        return R.ok(clientAppService.create(request));
    }

    /** 编辑（不含状态） */
    @SaCheckPermission("client:edit")
    @OperationLog(module = "client", type = "edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody ClientUpsertRequest request) {
        clientAppService.update(id, request);
        return R.ok();
    }

    /** 启停用（FR-CLIENT-03：停用即刻使 python-sandbox 端拒绝其名下启用 ApiKey） */
    @SaCheckPermission("client:disable")
    @OperationLog(module = "client", type = "disable")
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        clientAppService.changeStatus(id, status);
        return R.ok();
    }

    /** 删除（FR-CLIENT-04：仍持有有效 ApiKey 时阻断并提示先处理） */
    @SaCheckPermission("client:delete")
    @OperationLog(module = "client", type = "delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        clientAppService.delete(id);
        return R.ok();
    }

    /** 统计卡片（T-0035，FR-CLIENT-05：ApiKey 数/活跃数/今日调用/累计调用） */
    @SaCheckPermission("client:view")
    @GetMapping("/{id}/stats")
    public R<io.github.sandbox.admin.client.dto.ClientStatsVO> stats(@PathVariable Long id) {
        return R.ok(clientAppService.stats(id));
    }

    /** 归属转移（T-0035，FR-CLIENT-06：仅管理员；历史调用记录归属同步更新并落审计） */
    @SaCheckPermission("client:edit")
    @OperationLog(module = "client", type = "edit")
    @PutMapping("/{id}/owner")
    public R<Void> transferOwner(@PathVariable Long id, @RequestBody Map<String, Long> body) {
        clientAppService.transferOwner(id, body.get("ownerUserId"));
        return R.ok();
    }
}
