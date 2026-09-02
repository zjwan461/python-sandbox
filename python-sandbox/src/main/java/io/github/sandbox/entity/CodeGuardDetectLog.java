package io.github.sandbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * CodeGuard 模型推理检测记录实体（表 schema 见 cross-cutting/database/schema/008-codeguard-detect-log.sql）。
 *
 * <p>每次调用推理服务（POST /detect）后异步落库一条：既作调用审计，
 * 也供 train/ 侧导出再训练样本（数据飞轮）。</p>
 */
@Data
@TableName("codeguard_detect_log")
public class CodeGuardDetectLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求追踪ID（关联 api_log.trace_id） */
    private String traceId;

    /** 沙箱会话ID */
    private String sessionId;

    /** 归属客户端（client_app.id；匿名/无上下文为 null） */
    private Long clientId;

    /** 调用 ApiKey（client_api_key.id） */
    private Long apiKeyId;

    /** 归属用户（admin_user.id） */
    private Long ownerUserId;

    /** 送检代码片段原文（再训练样本源） */
    private String codeSnippet;

    /** 代码字符数 */
    private Integer codeLength;

    /** 推理模型标识（沙箱侧配置，便于多模型对比） */
    private String modelName;

    /** 模型判定标签：SAFE / DANGEROUS（服务异常为 null） */
    private String label;

    /** 判定危险标志：1=DANGEROUS 0=SAFE null=未取得结果 */
    private Integer dangerous;

    /** 模型原始输出文本 */
    private String rawOutput;

    /** 调用状态：OK=成功 SERVICE_ERROR=推理服务不可用/异常 */
    private String detectStatus;

    /** 最终处置：ALLOW / BLOCK / FAIL_OPEN / FAIL_CLOSE */
    private String decision;

    /** 推理调用耗时（毫秒） */
    private Long latencyMs;

    /** SERVICE_ERROR 时的错误摘要 */
    private String errorMessage;

    /** 创建时间（DB 默认 CURRENT_TIMESTAMP，与日志表 created_at 口径一致） */
    private LocalDateTime createdAt;
}
