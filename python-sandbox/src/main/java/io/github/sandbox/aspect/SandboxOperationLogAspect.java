package io.github.sandbox.aspect;

import io.github.sandbox.entity.SandboxOperationLog;
import io.github.sandbox.service.AsyncLogService;
import io.github.sandbox.service.SandboxService.CommandResult;
import io.github.sandbox.util.TraceUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 沙箱操作日志切面
 * 自动记录沙箱中的Python执行、Shell执行、pip操作等
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SandboxOperationLogAspect {

    private final AsyncLogService asyncLogService;

    /**
     * 匹配SandboxService中的关键操作方法
     */
    @Pointcut("execution(* io.github.sandbox.service.SandboxService.runPythonCode(..)) || " +
              "execution(* io.github.sandbox.service.SandboxService.execInContainer(..)) || " +
              "execution(* io.github.sandbox.service.SandboxService.pipInstall(..)) || " +
              "execution(* io.github.sandbox.service.SandboxService.pipUninstall(..)) || " +
              "execution(* io.github.sandbox.service.SandboxService.pipList(..))")
    public void sandboxOperationPointcut() {}

    @Around("sandboxOperationPointcut()")
    public Object logSandboxOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        
        // 确定操作类型和内容
        String operationType = mapMethodToOperationType(methodName);
        String sessionId = args.length > 0 ? String.valueOf(args[0]) : null;
        String operationContent = extractOperationContent(methodName, args);
        
        String traceId = TraceUtil.getTraceId();
        
        try {
            // 执行目标方法
            Object result = joinPoint.proceed();
            
            // 记录成功日志
            long executionTime = System.currentTimeMillis() - startTime;
            logOperation(traceId, sessionId, operationType, operationContent, 
                    "SUCCESS", result, executionTime, null);
            
            return result;
        } catch (Throwable e) {
            // 记录失败日志
            long executionTime = System.currentTimeMillis() - startTime;
            logOperation(traceId, sessionId, operationType, operationContent, 
                    "FAILED", null, executionTime, e.getMessage());
            throw e;
        }
    }
    
    private String mapMethodToOperationType(String methodName) {
        switch (methodName) {
            case "runPythonCode":
                return "PYTHON_EXEC";
            case "execInContainer":
                return "SHELL_EXEC";
            case "pipInstall":
                return "PIP_INSTALL";
            case "pipUninstall":
                return "PIP_UNINSTALL";
            case "pipList":
                return "PIP_LIST";
            default:
                return "UNKNOWN";
        }
    }
    
    private String extractOperationContent(String methodName, Object[] args) {
        try {
            switch (methodName) {
                case "runPythonCode":
                    // args: [sessionId, code]
                    if (args.length >= 2) {
                        String code = String.valueOf(args[1]);
                        return truncate(code, 50000);
                    }
                    break;
                case "execInContainer":
                    // args: [sessionId, ...commands]
                    StringBuilder cmd = new StringBuilder();
                    for (int i = 1; i < args.length; i++) {
                        if (i > 1) cmd.append(" ");
                        cmd.append(args[i]);
                    }
                    return truncate(cmd.toString(), 10000);
                case "pipInstall":
                case "pipUninstall":
                    // args: [sessionId, packageName]
                    if (args.length >= 2) {
                        return String.valueOf(args[1]);
                    }
                    break;
                case "pipList":
                    return "list";
                default:
                    break;
            }
        } catch (Exception e) {
            log.warn("提取操作内容失败: {}", e.getMessage());
        }
        return null;
    }
    
    private void logOperation(String traceId, String sessionId, String operationType,
                              String operationContent, String result, Object commandResult,
                              long executionTime, String errorMessage) {
        try {
            SandboxOperationLog opLog = new SandboxOperationLog();
            opLog.setTraceId(traceId);
            opLog.setSessionId(sessionId);
            opLog.setOperationType(operationType);
            opLog.setOperationContent(operationContent);
            opLog.setResult(result);
            opLog.setExecutionTime(executionTime);
            opLog.setErrorMessage(errorMessage);
            opLog.setCreatedAt(LocalDateTime.now());
            
            // 如果结果是CommandResult，提取详细信息
            if (commandResult instanceof CommandResult) {
                CommandResult cr = (CommandResult) commandResult;
                opLog.setExitCode(cr.getExitCode());
                opLog.setStdout(truncate(cr.getStdout(), 50000));
                opLog.setStderr(truncate(cr.getStderr(), 50000));
            }
            
            asyncLogService.logOperationAsync(opLog);
        } catch (Exception e) {
            log.error("构建操作日志失败: {}", e.getMessage(), e);
        }
    }
    
    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength) + "...(truncated)";
    }
}
