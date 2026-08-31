package io.github.sandbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API 请求日志实体
 */
@Data
@TableName("api_log")
public class ApiLog {
    
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
     * API路径
     */
    private String apiPath;
    
    /**
     * HTTP方法
     */
    private String httpMethod;
    
    /**
     * 请求参数（JSON格式）
     */
    private String requestParams;
    
    /**
     * 响应状态码
     */
    private Integer responseCode;
    
    /**
     * 执行耗时（毫秒）
     */
    private Long executionTime;
    
    /**
     * 客户端IP
     */
    private String clientIp;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
