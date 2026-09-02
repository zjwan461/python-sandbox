package io.github.sandbox.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sandbox.context.AuthContext;
import io.github.sandbox.entity.ApiLog;
import io.github.sandbox.service.AsyncLogService;
import io.github.sandbox.util.TraceUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * API日志切面
 * 自动记录所有Controller层的API请求日志
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ApiLogAspect {

    private final AsyncLogService asyncLogService;
    private final ObjectMapper objectMapper;

    @Around("within(io.github.sandbox.controller..*)")
    public Object logApiRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        
        // 获取请求信息
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes != null ? attributes.getRequest() : null;
        
        String traceId = TraceUtil.getTraceId();
        String apiPath = request != null ? request.getRequestURI() : "unknown";
        String httpMethod = request != null ? request.getMethod() : "unknown";
        String clientIp = request != null ? TraceUtil.getClientIp(request) : "unknown";
        String sessionId = extractSessionId(joinPoint.getArgs());
        
        int responseCode = 200;
        try {
            // 执行目标方法
            Object result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            // 根据异常类型推断响应码
            responseCode = inferStatusCode(e);
            throw e;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            
            // 构建API日志
            ApiLog apiLog = new ApiLog();
            apiLog.setTraceId(traceId);
            apiLog.setSessionId(sessionId);
            apiLog.setApiPath(apiPath);
            apiLog.setHttpMethod(httpMethod);
            apiLog.setRequestParams(safeSerialize(joinPoint.getArgs()));
            apiLog.setResponseCode(responseCode);
            apiLog.setExecutionTime(executionTime);
            apiLog.setClientIp(clientIp);
            apiLog.setCreatedAt(LocalDateTime.now());

            // 归属字段填充（T-0022/T-0023）：从鉴权线程上下文读取；无上下文时保持 NULL
            AuthContext.Principal principal = AuthContext.getPrincipal();
            if (principal != null) {
                apiLog.setClientId(principal.getClientId());
                apiLog.setApiKeyId(principal.getApiKeyId());
                apiLog.setOwnerUserId(principal.getOwnerUserId());
            }
            // 限流命中标志与规则标识（T-0022，design.md §7.5；命中拒绝路径不进入本切面）
            AuthContext.RateLimitHit rlHit = AuthContext.getRateLimitHit();
            apiLog.setRateLimitHit(rlHit != null ? 1 : 0);
            if (rlHit != null) {
                apiLog.setRateLimitRuleId(rlHit.getRuleId());
            }

            // 异步记录日志
            asyncLogService.logApiAsync(apiLog);
            
            // 清理追踪ID
            TraceUtil.clearTraceId();
        }
    }
    
    /**
     * 从方法参数中提取sessionId
     */
    private String extractSessionId(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg == null) continue;
            try {
                // 尝试从请求对象中获取sessionId字段
                java.lang.reflect.Method getSessionId = arg.getClass().getMethod("getSessionId");
                Object sessionId = getSessionId.invoke(arg);
                if (sessionId != null) {
                    return sessionId.toString();
                }
            } catch (Exception ignored) {
                // 忽略没有getSessionId方法的参数
            }
        }
        return null;
    }
    
    /**
     * 安全序列化参数，避免序列化失败影响业务
     */
    private String safeSerialize(Object[] args) {
        try {
            // 过滤掉文件等不可序列化的参数
            Object[] filteredArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof MultipartFile) {
                    filteredArgs[i] = "file:" + ((MultipartFile) args[i]).getOriginalFilename();
                } else if (args[i] instanceof byte[]) {
                    filteredArgs[i] = "byte[" + ((byte[]) args[i]).length + "]";
                } else {
                    filteredArgs[i] = args[i];
                }
            }
            String json = objectMapper.writeValueAsString(filteredArgs);
            // 限制日志长度，避免超长内容占用过多存储空间
            if (json.length() > 10000) {
                json = json.substring(0, 10000) + "...(truncated)";
            }
            return json;
        } catch (Exception e) {
            log.warn("序列化请求参数失败: {}", e.getMessage());
            return "serialization_failed";
        }
    }
    
    /**
     * 根据异常推断HTTP状态码
     */
    private int inferStatusCode(Throwable e) {
        String className = e.getClass().getSimpleName();
        if (className.contains("SandboxException")) {
            return 500;
        } else if (className.contains("Validation") || className.contains("IllegalArgument")) {
            return 400;
        } else if (className.contains("NotFound")) {
            return 404;
        }
        return 500;
    }
}
