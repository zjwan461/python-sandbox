package io.github.sandbox.admin.rbac.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.common.result.R;
import io.github.sandbox.admin.rbac.dto.MenuRouteVO;
import io.github.sandbox.admin.rbac.dto.MenuTreeVO;
import io.github.sandbox.admin.rbac.entity.AdminMenu;
import io.github.sandbox.admin.rbac.service.AdminMenuService;
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
 * 菜单管理接口（T-0019/T-0039 后端部分，design.md §10.3：/admin-api/menus）。
 *
 * <p>/menus/routes 仅要求登录（动态路由源，所有登录用户可取自身可见树）。
 * 批次6（T-0039）新增：批量排序 batch-sort、可见性切换 {id}/visible。</p>
 */
@RestController
@RequestMapping("/menus")
@RequiredArgsConstructor
public class MenuController {

    private final AdminMenuService adminMenuService;

    /** 全量菜单树（管理页，含按钮） */
    @SaCheckPermission("menu:view")
    @GetMapping("/tree")
    public R<List<MenuTreeVO>> tree() {
        return R.ok(adminMenuService.tree());
    }

    /** 当前用户可见动态路由树（按角色过滤，仅 M/C；T-0039 起按 isVisible 收敛） */
    @GetMapping("/routes")
    public R<List<MenuRouteVO>> routes() {
        return R.ok(adminMenuService.routes());
    }

    /** 新增菜单 */
    @SaCheckPermission("menu:add")
    @OperationLog(module = "menu", type = "add")
    @PostMapping
    public R<Long> create(@Validated @RequestBody AdminMenu menu) {
        return R.ok(adminMenuService.create(menu));
    }

    /** 编辑菜单 */
    @SaCheckPermission("menu:edit")
    @OperationLog(module = "menu", type = "edit")
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody AdminMenu menu) {
        adminMenuService.update(id, menu);
        return R.ok();
    }

    /** 删除菜单（存在子节点拒绝） */
    @SaCheckPermission("menu:delete")
    @OperationLog(module = "menu", type = "delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        adminMenuService.delete(id);
        return R.ok();
    }

    /** 批量排序（T-0039，FR-MENU-03：拖拽后提交同父级有序ID清单） */
    @SaCheckPermission("menu:edit")
    @OperationLog(module = "menu", type = "edit")
    @PutMapping("/batch-sort")
    public R<Void> batchSort(@RequestBody List<Long> orderedIds) {
        adminMenuService.batchSort(orderedIds);
        return R.ok();
    }

    /** 可见性切换（T-0039，FR-MENU-03：目录/菜单显隐立即反映到当前用户可见路由） */
    @SaCheckPermission("menu:edit")
    @OperationLog(module = "menu", type = "edit")
    @PutMapping("/{id}/visible")
    public R<Void> changeVisible(@PathVariable Long id, @RequestParam Integer isVisible) {
        adminMenuService.changeVisible(id, isVisible);
        return R.ok();
    }
}
