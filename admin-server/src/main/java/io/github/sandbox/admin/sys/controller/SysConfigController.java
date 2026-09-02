package io.github.sandbox.admin.sys.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.common.result.R;
import io.github.sandbox.admin.sys.entity.SysConfig;
import io.github.sandbox.admin.sys.service.SysConfigAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 系统设置管理接口（T-0041，design.md §10.3：/admin-api/sys/configs）。
 *
 * <p>GET 受控列表（sysconfig:view，审计员只读可访问）；
 * PUT /batch 批量更新（sysconfig:edit，仅超管/管理员，普通用户后端独立拒绝），
 * 写操作以 module=sysconfig type=edit 落审计。更新内容仅键值对，
 * 敏感凭证不在 sys_config 登记范围内（无法经此配置）。</p>
 */
@RestController
@RequestMapping("/sys/configs")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigAdminService sysConfigAdminService;

    /** 受控配置项列表 */
    @SaCheckPermission("sysconfig:view")
    @GetMapping
    public R<List<SysConfig>> list() {
        return R.ok(sysConfigAdminService.list());
    }

    /** 批量更新（configKey -> value；未登记键或类型非法整体拒绝） */
    @SaCheckPermission("sysconfig:edit")
    @OperationLog(module = "sysconfig", type = "edit")
    @PutMapping("/batch")
    public R<Void> batchUpdate(@RequestBody Map<String, String> updates) {
        sysConfigAdminService.batchUpdate(updates);
        return R.ok();
    }
}
