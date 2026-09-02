package io.github.sandbox.admin.log.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 沙箱操作日志只读视图实体（表 sandbox_operation_log，python-sandbox 写入，admin-server 只读查询，T-0032）。
 *
 * <p>字段 = python-sandbox 既有列（init.sql）+ cross-cutting schema/006 归属扩展列。
 * 独立定义、不 import python-sandbox 任何类。本表无逻辑删除/公共审计列，不继承 BaseEntity。</p>
 */
@Data
@TableName("sandbox_operation_log")
public class SandboxOperationLogView implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 请求追踪ID */
    private String traceId;

    /** 会话ID */
    private String sessionId;

    /** 操作类型：PYTHON_EXEC / SHELL_EXEC / PIP_INSTALL / PIP_UNINSTALL / PIP_LIST */
    private String operationType;

    /** 操作内容（可能被写入侧截断，以 "...(truncated)" 结尾） */
    private String operationContent;

    /** 执行结果：SUCCESS / FAILED */
    private String result;

    /** 退出码 */
    private Integer exitCode;

    /** 标准输出（可能被截断） */
    private String stdout;

    /** 标准错误（可能被截断） */
    private String stderr;

    /** 执行耗时（毫秒） */
    private Long executionTime;

    /** 错误信息 */
    private String errorMessage;

    /** 创建时间 */
    private LocalDateTime createdAt;

    // ===== schema/006 扩展列 =====

    /** 归属客户端（client_app.id） */
    private Long clientId;

    /** 归属 ApiKey（client_api_key.id） */
    private Long apiKeyId;

    /** 归属用户（admin_user.id，数据权限 SELF 过滤键） */
    private Long ownerUserId;
}
