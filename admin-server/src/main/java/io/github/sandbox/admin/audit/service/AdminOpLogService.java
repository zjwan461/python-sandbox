package io.github.sandbox.admin.audit.service;

import io.github.sandbox.admin.audit.entity.AdminOpLog;
import io.github.sandbox.admin.audit.mapper.AdminOpLogMapper;
import io.github.sandbox.admin.common.security.AdminLoginUser;
import io.github.sandbox.admin.common.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 操作审计写入服务（T-0020 后端基础设施部分）：只追加，不开放修改/删除。
 *
 * <p>批次2 内由用户/角色/菜单管理与登录联动调用；
 * 批次3（客户端/ApiKey/限流/会话）可直接复用 {@link #record} 入口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminOpLogService {

    private final AdminOpLogMapper adminOpLogMapper;

    /**
     * 记录一条操作审计（操作人取当前登录用户；无登录上下文时以 system 落库）。
     *
     * @param module        模块（user/role/menu/client/apikey/ratelimit/session/bridge/sysconfig）
     * @param operationType 操作类型（add/edit/delete/enable/disable/revoke/reset/force）
     * @param targetId      目标对象主键（字符串承载）
     * @param targetName    目标对象名/编码（默认决策 #12）
     * @param changeSummary 关键字段变更摘要（可为 null）
     */
    public void record(String module, String operationType, String targetId,
                       String targetName, String changeSummary) {
        write(module, operationType, targetId, targetName, changeSummary, "SUCCESS", null);
    }

    /** 记录失败结果（业务异常联动用） */
    public void recordFail(String module, String operationType, String targetId,
                           String targetName, String failReason) {
        write(module, operationType, targetId, targetName, null, "FAIL", failReason);
    }

    private void write(String module, String operationType, String targetId, String targetName,
                       String changeSummary, String result, String failReason) {
        try {
            AdminOpLog entity = new AdminOpLog();
            AdminLoginUser user = SecurityUtils.getLoginUserQuietly();
            if (user != null) {
                entity.setOperatorId(user.getUserId());
                entity.setOperatorName(user.getUsername());
            } else {
                entity.setOperatorId(0L);
                entity.setOperatorName("system");
            }
            entity.setModule(module);
            entity.setOperationType(operationType);
            entity.setTargetId(targetId);
            entity.setTargetName(truncate(targetName, 200));
            entity.setChangeSummary(changeSummary);
            entity.setResult(result);
            entity.setFailReason(truncate(failReason, 255));
            entity.setOpTime(LocalDateTime.now());
            entity.setTraceId(MDC.get("traceId"));
            HttpServletRequest req = currentRequest();
            if (req != null) {
                entity.setIp(clientIp(req));
                String ua = req.getHeader("User-Agent");
                entity.setUserAgent(ua != null && ua.length() > 512 ? ua.substring(0, 512) : ua);
            }
            adminOpLogMapper.insert(entity);
        } catch (Exception e) {
            // 审计写入失败不阻断业务主流程（生产可另接告警；本设计不展开可观测性）
            log.error("写入操作审计失败 module={} type={}: {}", module, operationType, e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
