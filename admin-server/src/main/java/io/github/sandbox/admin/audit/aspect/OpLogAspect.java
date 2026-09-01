package io.github.sandbox.admin.audit.aspect;

import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.audit.service.AdminOpLogService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 写操作审计切面（T-0020 基础设施部分）：拦截标注 {@link OperationLog} 的
 * Controller 方法，成功后落 admin_op_log；业务异常不记录（由调用方按需记录失败）。
 *
 * <p>targetId/targetName 的精细提取（SpEL 等）不在本批次展开，
 * 记录方法入参摘要作为 change_summary，满足"主键+对象名"的最低可追溯口径，
 * 批次3 可按需增强。</p>
 */
@Aspect
@Component
@RequiredArgsConstructor
public class OpLogAspect {

    private final AdminOpLogService adminOpLogService;

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog operationLog) throws Throwable {
        Object result = pjp.proceed();
        String summary = buildSummary(pjp);
        adminOpLogService.record(operationLog.module(), operationLog.type(),
                extractFirstArg(pjp), null, summary);
        return result;
    }

    private String buildSummary(ProceedingJoinPoint pjp) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        return "{\"method\":\"" + sig.getDeclaringType().getSimpleName() + "." + sig.getName() + "\","
                + "\"args\":" + Arrays.toString(pjp.getArgs()) + "}";
    }

    /** 首个参数作为目标对象主键的启发式提取（Path 变量惯例为第一位） */
    private String extractFirstArg(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args.length > 0 && args[0] != null
                && (args[0] instanceof Number || args[0] instanceof String)) {
            String s = String.valueOf(args[0]);
            return s.length() > 64 ? s.substring(0, 64) : s;
        }
        return null;
    }
}
