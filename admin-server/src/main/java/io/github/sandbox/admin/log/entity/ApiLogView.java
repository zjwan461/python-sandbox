package io.github.sandbox.admin.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * API 调用日志只读视图实体（表 api_log，python-sandbox 写入，admin-server 只读查询，T-0032）。
 *
 * <p>字段 = python-sandbox 既有列（init.sql）+ cross-cutting schema/006 扩展列。
 * 独立定义、不 import python-sandbox 任何类（T-0001 代码隔离边界）。
 * 本表无逻辑删除/公共审计列，故不继承 BaseEntity。</p>
 */
@Data
@TableName("api_log")
public class ApiLogView implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求追踪ID */
    private String traceId;

    /** 会话ID */
    private String sessionId;

    /** API路径 */
    private String apiPath;

    /** HTTP方法 */
    private String httpMethod;

    /** 请求参数（JSON，可能被写入侧截断，以 "...(truncated)" 结尾） */
    private String requestParams;

    /** 响应状态码 */
    private Integer responseCode;

    /** 执行耗时（毫秒） */
    private Long executionTime;

    /** 客户端IP */
    private String clientIp;

    /** 创建时间 */
    private LocalDateTime createdAt;

    // ===== schema/006 扩展列 =====

    /** 归属客户端（client_app.id；无鉴权上下文为 NULL） */
    private Long clientId;

    /** 调用 ApiKey（client_api_key.id；匿名调用为 NULL） */
    private Long apiKeyId;

    /** 归属用户（admin_user.id，数据权限 SELF 过滤键） */
    private Long ownerUserId;

    /** 限流命中标志：0=未命中 1=命中（默认决策 #2） */
    private Integer rateLimitHit;

    /** 命中的限流规则主键（ratelimit_rule.id；未命中为 NULL） */
    private Long rateLimitRuleId;
}
