package io.github.sandbox.admin.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * CodeGuard 模型推理检测记录只读视图实体（表 codeguard_detect_log，
 * python-sandbox 写入，admin-server 只读查询）。
 *
 * <p>独立定义、不 import python-sandbox 任何类。本表无逻辑删除/公共审计列，
 * 不继承 BaseEntity。字段口径见 cross-cutting/database/schema/008-codeguard-detect-log.sql。</p>
 */
@Data
@TableName("codeguard_detect_log")
public class CodeGuardDetectLogView implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求追踪ID（关联 api_log.trace_id） */
    private String traceId;

    /** 沙箱会话ID */
    private String sessionId;

    /** 归属客户端（client_app.id） */
    private Long clientId;

    /** 调用 ApiKey（client_api_key.id） */
    private Long apiKeyId;

    /** 归属用户（admin_user.id，数据权限 SELF 过滤键） */
    private Long ownerUserId;

    /** 送检代码片段原文（再训练样本源） */
    private String codeSnippet;

    /** 代码字符数 */
    private Integer codeLength;

    /** 推理模型标识 */
    private String modelName;

    /** 模型判定标签：SAFE / DANGEROUS（服务异常为 null） */
    private String label;

    /** 判定危险标志：1=DANGEROUS 0=SAFE null=未取得结果 */
    private Integer dangerous;

    /** 模型原始输出文本 */
    private String rawOutput;

    /** 调用状态：OK / SERVICE_ERROR */
    private String detectStatus;

    /** 最终处置：ALLOW / BLOCK / FAIL_OPEN / FAIL_CLOSE */
    private String decision;

    /** 推理调用耗时（毫秒） */
    private Long latencyMs;

    /** SERVICE_ERROR 时的错误摘要 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
