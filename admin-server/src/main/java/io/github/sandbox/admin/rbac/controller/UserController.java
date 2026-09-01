package io.github.sandbox.admin.rbac.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.result.R;
import io.github.sandbox.admin.rbac.dto.UserQuery;
import io.github.sandbox.admin.rbac.dto.UserUpsertRequest;
import io.github.sandbox.admin.rbac.dto.UserVO;
import io.github.sandbox.admin.rbac.service.AdminUserService;
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

import java.util.List;
import java.util.Map;

/**
 * 用户管理接口（T-0018 后端部分，design.md §10.3：/admin-api/users）。
 *
 * <p>按钮权限经 @SaCheckPermission 服务端校验（前端隐藏不可绕过）。</p>
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final AdminUserService adminUserService;

    /** 分页列表 + 筛选 */
    @SaCheckPermission("user:view")
    @GetMapping
    public R<PageResult<UserVO>> page(UserQuery query) {
        return R.ok(adminUserService.page(query));
    }

    /** 详情 */
    @SaCheckPermission("user:view")
    @GetMapping("/{id}")
    public R<UserVO> detail(@PathVariable Long id) {
        return R.ok(adminUserService.detail(id));
    }

    /** 新增 */
    @SaCheckPermission("user:add")
    @OperationLog(module = "user", type = "add")
    @PostMapping
    public R<Long> create(@Valid @RequestBody UserUpsertRequest request) {
        return R.ok(adminUserService.create(request));
    }

    /** 编辑（不含密码与状态） */
    @SaCheckPermission("user:edit")
    @OperationLog(module = "user", type = "edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpsertRequest request) {
        adminUserService.update(id, request);
        return R.ok();
    }

    /** 启停用 */
    @SaCheckPermission("user:disable")
    @OperationLog(module = "user", type = "disable")
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminUserService.changeStatus(id, status);
        return R.ok();
    }

    /** 管理员重置密码 */
    @SaCheckPermission("user:reset")
    @OperationLog(module = "user", type = "reset")
    @PutMapping("/{id}/reset-password")
    public R<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        adminUserService.resetPassword(id, body.get("newPassword"));
        return R.ok();
    }

    /** 手动解锁（FR-AUTH-05） */
    @SaCheckPermission("user:edit")
    @OperationLog(module = "user", type = "edit")
    @PutMapping("/{id}/unlock")
    public R<Void> unlock(@PathVariable Long id) {
        adminUserService.unlock(id);
        return R.ok();
    }

    /** 分配角色 */
    @SaCheckPermission("user:edit")
    @OperationLog(module = "user", type = "edit")
    @PutMapping("/{id}/roles")
    public R<Void> assignRoles(@PathVariable Long id, @RequestBody List<Long> roleIds) {
        adminUserService.assignRoles(id, roleIds);
        return R.ok();
    }
}
