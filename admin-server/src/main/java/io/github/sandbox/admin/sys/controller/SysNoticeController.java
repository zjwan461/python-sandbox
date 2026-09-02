package io.github.sandbox.admin.sys.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.result.R;
import io.github.sandbox.admin.sys.dto.SysNoticeUpsertRequest;
import io.github.sandbox.admin.sys.dto.SysNoticeVO;
import io.github.sandbox.admin.sys.service.SysNoticeService;
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

/**
 * 通知公告接口（T-0042，design.md §10.3：/admin-api/sys/notices 管理端点 + /notices 投递端点）。
 *
 * <p>管理端点按 notice:add/edit/delete 权限码约束（写操作经 @OperationLog 落审计）；
 * 投递端点（inbox/unread-count/read）仅要求登录——普通用户只读不能管理（验收）。</p>
 */
@RestController
@RequiredArgsConstructor
public class SysNoticeController {

    private final SysNoticeService sysNoticeService;

    // ===================== 管理侧 =====================

    /** 公告管理列表（标题/状态筛选） */
    @SaCheckPermission("notice:view")
    @GetMapping("/sys/notices")
    public R<PageResult<SysNoticeVO>> page(@RequestParam(required = false) String title,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(defaultValue = "1") long pageNum,
                                           @RequestParam(defaultValue = "20") long pageSize) {
        return R.ok(sysNoticeService.pageForAdmin(title, status, pageNum, pageSize));
    }

    /** 新增（草稿） */
    @SaCheckPermission("notice:add")
    @OperationLog(module = "notice", type = "add")
    @PostMapping("/sys/notices")
    public R<Long> create(@Valid @RequestBody SysNoticeUpsertRequest request) {
        return R.ok(sysNoticeService.create(request));
    }

    /** 编辑 */
    @SaCheckPermission("notice:edit")
    @OperationLog(module = "notice", type = "edit")
    @PutMapping("/sys/notices/{id}")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody SysNoticeUpsertRequest request) {
        sysNoticeService.update(id, request);
        return R.ok();
    }

    /** 删除（逻辑删除） */
    @SaCheckPermission("notice:delete")
    @OperationLog(module = "notice", type = "delete")
    @DeleteMapping("/sys/notices/{id}")
    public R<Void> delete(@PathVariable Long id) {
        sysNoticeService.delete(id);
        return R.ok();
    }

    /** 发布（投递生效） */
    @SaCheckPermission("notice:edit")
    @OperationLog(module = "notice", type = "edit")
    @PutMapping("/sys/notices/{id}/publish")
    public R<Void> publish(@PathVariable Long id) {
        sysNoticeService.publish(id);
        return R.ok();
    }

    /** 下线（停止投递） */
    @SaCheckPermission("notice:edit")
    @OperationLog(module = "notice", type = "edit")
    @PutMapping("/sys/notices/{id}/unpublish")
    public R<Void> unpublish(@PathVariable Long id) {
        sysNoticeService.unpublish(id);
        return R.ok();
    }

    // ===================== 投递侧（登录即可） =====================

    /** 当前用户公告站内信列表（已发布+有效窗口，含已读标记，置顶优先） */
    @GetMapping("/notices/inbox")
    public R<List<SysNoticeVO>> inbox() {
        return R.ok(sysNoticeService.visibleForCurrentUser());
    }

    /** 未读数量（顶部通栏徽标） */
    @GetMapping("/notices/unread-count")
    public R<Long> unreadCount() {
        return R.ok(sysNoticeService.unreadCount());
    }

    /** 标记已读（幂等） */
    @PutMapping("/notices/{id}/read")
    public R<Void> markRead(@PathVariable Long id) {
        sysNoticeService.markRead(id);
        return R.ok();
    }
}
