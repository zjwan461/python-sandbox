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
import java.util.Map;

/**
 * 用户管理接口（T-0018，design.md §10.3：/admin-api/users）。
 *
 * <p>按钮权限经 @SaCheckPermission 服务端校验（前端隐藏不可绕过）。
 * 批次6 补齐：单删/批删（软删除+历史归属转移，FR-USER-03）。</p>
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

    /** 单删（T-0018 批次6：软删除+历史归属转移；已登录/持有有效 ApiKey 阻止，12006） */
    @SaCheckPermission("user:delete")
    @OperationLog(module = "user", type = "delete")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        adminUserService.delete(List.of(id));
        return R.ok();
    }

    /** 批量删除（口径同单删；任一目标不满足业务规则即整批拒绝并透出原因） */
    @SaCheckPermission("user:delete")
    @OperationLog(module = "user", type = "delete")
    @DeleteMapping("/batch")
    public R<Void> deleteBatch(@RequestBody List<Long> ids) {
        adminUserService.delete(ids);
        return R.ok();
    }

    /** CSV 导出（T-0043，FR-USER-07：范围=当前筛选+数据权限；不含密码/ApiKey/凭证） */
    @SaCheckPermission("user:export")
    @OperationLog(module = "user", type = "export")
    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<byte[]> export(UserQuery query) {
        byte[] body = adminUserService.exportUsers(query);
        String fileName = "users_" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".csv";
        String encoded = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
                .replace("+", "%20");
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded)
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    /** CSV 批量导入（T-0043：逐行反馈成功/失败；重复用户名与非法字段不静默覆盖） */
    @SaCheckPermission("user:import")
    @OperationLog(module = "user", type = "add")
    @PostMapping("/import")
    public R<io.github.sandbox.admin.rbac.dto.UserImportResultVO> importUsers(
            @org.springframework.web.bind.annotation.RequestParam("file")
            org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam("initialPassword") String initialPassword) {
        try {
            return R.ok(adminUserService.importUsers(file.getBytes(), initialPassword));
        } catch (java.io.IOException e) {
            throw new io.github.sandbox.admin.common.exception.BusinessException(
                    io.github.sandbox.admin.common.exception.ErrorCode.PARAM_ERROR, "读取上传文件失败");
        }
    }
}
