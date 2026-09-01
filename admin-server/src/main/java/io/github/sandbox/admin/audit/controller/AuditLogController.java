package io.github.sandbox.admin.audit.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.sandbox.admin.audit.entity.AdminLoginLog;
import io.github.sandbox.admin.audit.entity.AdminOpLog;
import io.github.sandbox.admin.audit.mapper.AdminLoginLogMapper;
import io.github.sandbox.admin.audit.mapper.AdminOpLogMapper;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 管理端审计查询接口（T-0020 后端只读部分，design.md §10.3）。
 *
 * <p>口径：只追加日志，仅管理员/审计员可查（auditor 拥有 loginlog:view / oplog:view，
 * 普通用户无任何审计菜单授权，越权请求由 @SaCheckPermission 拒绝且不返回数据）。</p>
 */
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AdminLoginLogMapper loginLogMapper;
    private final AdminOpLogMapper opLogMapper;

    /** 登录日志分页 */
    @SaCheckPermission("loginlog:view")
    @GetMapping("/logins")
    public R<PageResult<AdminLoginLog>> logins(
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        LambdaQueryWrapper<AdminLoginLog> wrapper = Wrappers.<AdminLoginLog>lambdaQuery()
                .like(StringUtils.hasText(username), AdminLoginLog::getUsername, username)
                .eq(StringUtils.hasText(result), AdminLoginLog::getResult, result)
                .ge(beginTime != null, AdminLoginLog::getLoginTime, beginTime)
                .le(endTime != null, AdminLoginLog::getLoginTime, endTime)
                .orderByDesc(AdminLoginLog::getLoginTime);
        return R.ok(PageResult.of(loginLogMapper.selectPage(toPage(pageNum, pageSize), wrapper)));
    }

    /** 操作日志分页 */
    @SaCheckPermission("oplog:view")
    @GetMapping("/operations")
    public R<PageResult<AdminOpLog>> operations(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beginTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        LambdaQueryWrapper<AdminOpLog> wrapper = Wrappers.<AdminOpLog>lambdaQuery()
                .eq(StringUtils.hasText(module), AdminOpLog::getModule, module)
                .eq(StringUtils.hasText(operationType), AdminOpLog::getOperationType, operationType)
                .like(StringUtils.hasText(operatorName), AdminOpLog::getOperatorName, operatorName)
                .eq(StringUtils.hasText(targetId), AdminOpLog::getTargetId, targetId)
                .ge(beginTime != null, AdminOpLog::getOpTime, beginTime)
                .le(endTime != null, AdminOpLog::getOpTime, endTime)
                .orderByDesc(AdminOpLog::getOpTime);
        return R.ok(PageResult.of(opLogMapper.selectPage(toPage(pageNum, pageSize), wrapper)));
    }

    /** 操作日志详情 */
    @SaCheckPermission("oplog:view")
    @GetMapping("/operations/{id}")
    public R<AdminOpLog> operationDetail(@PathVariable Long id) {
        return R.ok(opLogMapper.selectById(id));
    }

    private <T> Page<T> toPage(long pageNum, long pageSize) {
        return new Page<>(Math.max(1, pageNum), Math.min(Math.max(1, pageSize), 200));
    }
}
