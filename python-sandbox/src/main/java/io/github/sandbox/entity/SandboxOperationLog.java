package io.github.sandbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 沙箱操作日志实体
 */
@Data
@TableName("sandbox_operation_log")
public class SandboxOperationLog {
    
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 请求追踪ID
     */
    private String traceId;
    
    /**
     * 会话ID
     */
    private String sessionId;
    
    /**
     * 操作类型：PYTHON_EXEC, SHELL_EXEC, PIP_INSTALL, PIP_UNINSTALL, FILE_UPLOAD, FILE_WRITE等
     */
    private String operationType;
    
    /**
     * 操作内容（Python代码、Shell命令、包名等）
     */
    private String operationContent;
    
    /**
     * 执行结果：SUCCESS, FAILED
     */
    private String result;
    
    /**
     * 退出码
     */
    private Integer exitCode;
    
    /**
     * 标准输出
     */
    private String stdout;
    
    /**
     * 标准错误
     */
    private String stderr;
    
    /**
     * 执行耗时（毫秒）
     */
    private Long executionTime;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
