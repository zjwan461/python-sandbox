package io.github.sandbox.service;

import io.github.sandbox.entity.ApiLog;
import io.github.sandbox.entity.CodeGuardDetectLog;
import io.github.sandbox.entity.SandboxOperationLog;
import io.github.sandbox.mapper.ApiLogMapper;
import io.github.sandbox.mapper.CodeGuardDetectLogMapper;
import io.github.sandbox.mapper.SandboxOperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 异步日志服务
 * 使用异步方式记录日志，避免阻塞主线程
 * 日志记录失败不会影响正常功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AsyncLogService {
    
    private final ApiLogMapper apiLogMapper;
    private final SandboxOperationLogMapper sandboxOperationLogMapper;
    private final CodeGuardDetectLogMapper codeGuardDetectLogMapper;
    
    /**
     * 异步记录API日志
     * @param apiLog API日志对象
     */
    @Async("logExecutor")
    public void logApiAsync(ApiLog apiLog) {
        try {
            apiLogMapper.insert(apiLog);
            log.debug("API日志记录成功: traceId={}, apiPath={}", apiLog.getTraceId(), apiLog.getApiPath());
        } catch (Exception e) {
            // 日志记录失败不影响正常功能，仅记录错误日志
            log.error("API日志记录失败: traceId={}, error={}", apiLog.getTraceId(), e.getMessage(), e);
        }
    }
    
    /**
     * 异步记录沙箱操作日志
     * @param operationLog 操作日志对象
     */
    @Async("logExecutor")
    public void logOperationAsync(SandboxOperationLog operationLog) {
        try {
            sandboxOperationLogMapper.insert(operationLog);
            log.debug("操作日志记录成功: traceId={}, operationType={}", 
                    operationLog.getTraceId(), operationLog.getOperationType());
        } catch (Exception e) {
            // 日志记录失败不影响正常功能，仅记录错误日志
            log.error("操作日志记录失败: traceId={}, error={}", 
                    operationLog.getTraceId(), e.getMessage(), e);
        }
    }

    /**
     * 异步记录 CodeGuard 模型推理检测明细（审计 + 再训练数据回流）。
     *
     * @param detectLog 检测记录
     */
    @Async("logExecutor")
    public void logCodeGuardDetectAsync(CodeGuardDetectLog detectLog) {
        try {
            codeGuardDetectLogMapper.insert(detectLog);
            log.debug("CodeGuard检测记录成功: traceId={}, label={}, decision={}",
                    detectLog.getTraceId(), detectLog.getLabel(), detectLog.getDecision());
        } catch (Exception e) {
            // 记录失败不影响检测主流程，仅记录错误日志
            log.error("CodeGuard检测记录失败: traceId={}, error={}",
                    detectLog.getTraceId(), e.getMessage(), e);
        }
    }
}
