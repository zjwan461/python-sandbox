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
     * 归属客户端（client_app.id；无鉴权上下文为NULL）（T-0022）
     */
    private Long clientId;

    /**
     * 调用ApiKey（client_api_key.id；匿名调用为NULL）（T-0022）
     */
    private Long apiKeyId;

    /**
     * 归属用户（admin_user.id，数据权限 SELF 过滤键）（T-0022）
     */
    private Long ownerUserId;

    /**
     * 限流命中标志：0=未命中 1=命中（默认决策#2，不另建命中表）（T-0022）
     */
    private Integer rateLimitHit;

    /**
     * 命中的限流规则主键（ratelimit_rule.id；未命中为NULL）（T-0022）
     */
    private Long rateLimitRuleId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
