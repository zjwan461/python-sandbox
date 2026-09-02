package io.github.sandbox.admin.session.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.bridge.dto.SandboxSessionVO;
import io.github.sandbox.admin.bridge.dto.SessionDestroyResultVO;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.result.R;
import io.github.sandbox.admin.session.dto.SessionQuery;
import io.github.sandbox.admin.session.dto.SessionBatchDestroyRequest;
import io.github.sandbox.admin.session.dto.SessionQuery;
import io.github.sandbox.admin.session.service.SessionAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运行中会话管理接口（T-0031 后端部分，design.md §10.3：/admin-api/sessions、§11.4）。
 *
 * <p>列表/详情：session:view（普通用户仅见本人归属会话，SELF 过滤在 Service 完成）；
 * 强销：session:force（种子授权仅超管/管理员，FR-SESSION-04/05），
 * 强销结果原样回传（失败不虚构成功），并以 module=session type=force 落审计。</p>
 */
@RestController
@RequestMapping("/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionAdminService sessionAdminService;

    /** 活跃会话列表（python-sandbox 内存快照 + 管理端数据权限过滤 + 归属富化） */
    @SaCheckPermission("session:view")
    @GetMapping
    public R<PageResult<SandboxSessionVO>> page(SessionQuery query) {
        return R.ok(sessionAdminService.page(query));
    }

    /** 会话详情（越权/不存在统一 40001 语义） */
    @SaCheckPermission("session:view")
    @GetMapping("/{sessionId}")
    public R<SandboxSessionVO> detail(@PathVariable String sessionId) {
        return R.ok(sessionAdminService.detail(sessionId));
    }

    /** 强制销毁（管理员二次确认由前端承载；回执含成功/失败+剩余会话数，默认决策 #7） */
    @SaCheckPermission("session:force")
    @OperationLog(module = "session", type = "force")
    @DeleteMapping("/{sessionId}")
    public R<SessionDestroyResultVO> destroy(@PathVariable String sessionId) {
        return R.ok(sessionAdminService.destroy(sessionId));
    }

    /** 会话关联日志（T-0037，FR-SESSION-03：最近 API 日志与沙箱操作日志，遵守数据权限） */
    @SaCheckPermission("session:view")
    @GetMapping("/{sessionId}/logs")
    public R<io.github.sandbox.admin.session.dto.SessionRelatedLogsVO> relatedLogs(
            @PathVariable String sessionId,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int limit) {
        return R.ok(sessionAdminService.relatedLogs(sessionId, limit));
    }

    /** 批量清理预览（T-0044：按不活跃阈值统计目标数量供前端确认，默认会话不计入） */
    @SaCheckPermission("session:force")
    @GetMapping("/batch/preview")
    public R<Long> batchPreview(@RequestParam Integer inactiveMinutes) {
        return R.ok(sessionAdminService.countInactiveTargets(inactiveMinutes));
    }

    /**
     * 批量强销（T-0044，FR-SESSION-06）：仅 session:force（种子=超管/管理员）可执行；
     * 逐项回执（任一失败不虚构全部成功）；自动阈值口径不隐式纳入默认会话；
     * 整体动作与逐项结果进入审计。
     */
    @SaCheckPermission("session:force")
    @OperationLog(module = "session", type = "force")
    @PostMapping("/batch-destroy")
    public R<List<SessionDestroyResultVO>> batchDestroy(@RequestBody SessionBatchDestroyRequest request) {
        return R.ok(sessionAdminService.batchDestroy(request));
    }
}
