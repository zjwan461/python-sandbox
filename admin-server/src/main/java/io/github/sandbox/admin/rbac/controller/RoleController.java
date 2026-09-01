package io.github.sandbox.admin.rbac.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.result.R;
import io.github.sandbox.admin.rbac.entity.AdminRole;
import io.github.sandbox.admin.rbac.service.AdminRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口（T-0019 后端部分，design.md §10.3：/admin-api/roles）。
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final AdminRoleService adminRoleService;

    /** 分页列表 */
    @SaCheckPermission("role:view")
    @GetMapping("/list")
    public R<PageResult<AdminRole>> list(
            @RequestParam(required = false) String roleName,
            @RequestParam(required = false) String roleKey,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(adminRoleService.page(roleName, roleKey, status, pageNum, pageSize));
    }

    /** 启用角色下拉 */
    @SaCheckPermission("role:view")
    @GetMapping("/options")
    public R<List<AdminRole>> options() {
        return R.ok(adminRoleService.listEnabled());
    }

    /** 新增 */
    @SaCheckPermission("role:add")
    @OperationLog(module = "role", type = "add")
    @PostMapping
    public R<Long> create(@Validated @RequestBody AdminRole role) {
        return R.ok(adminRoleService.create(role));
    }

    /** 编辑 */
    @SaCheckPermission("role:edit")
    @OperationLog(module = "role", type = "edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody AdminRole role) {
        adminRoleService.update(id, role);
        return R.ok();
    }

    /** 删除（内置不可删、被引用不可删） */
    @SaCheckPermission("role:delete")
    @OperationLog(module = "role", type = "delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        adminRoleService.delete(id);
        return R.ok();
    }

    /** 启停用 */
    @SaCheckPermission("role:edit")
    @OperationLog(module = "role", type = "disable")
    @PutMapping("/{id}/status")
    public R<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminRoleService.changeStatus(id, status);
        return R.ok();
    }

    /** 查询角色已授权菜单ID */
    @SaCheckPermission("role:view")
    @GetMapping("/{id}/menus")
    public R<List<Long>> menus(@PathVariable Long id) {
        return R.ok(adminRoleService.menuIds(id));
    }

    /** 分配菜单权限（全量替换） */
    @SaCheckPermission("role:edit")
    @OperationLog(module = "role", type = "edit")
    @PutMapping("/{id}/menus")
    public R<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        adminRoleService.assignMenus(id, menuIds);
        return R.ok();
    }
}
