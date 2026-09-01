package io.github.sandbox.admin.audit.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端操作审计日志实体（表 admin_op_log，字段与 schema/004 严格一致）。
 *
 * <p>只追加口径：不继承 BaseEntity；默认决策 #12：记录目标主键 + 对象名/编码。</p>
 */
@Data
@TableName("admin_op_log")
public class AdminOpLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人ID（admin_user.id） */
    private Long operatorId;

    /** 操作人用户名（冗余） */
    private String operatorName;

    /** 模块：user / role / menu / client / apikey / ratelimit / session / bridge / sysconfig 等 */
    private String module;

    /** 操作类型：add / edit / delete / enable / disable / revoke / reset / force 等 */
    private String operationType;

    /** 目标对象主键（字符串承载） */
    private String targetId;

    /** 目标对象名/编码 */
    private String targetName;

    /** 关键字段变更摘要（JSON） */
    private String changeSummary;

    /** 结果：SUCCESS / FAIL */
    private String result;

    /** 失败原因 */
    private String failReason;

    /** 来源IP */
    private String ip;

    /** 浏览器UA */
    private String userAgent;

    /** 链路追踪ID */
    private String traceId;

    /** 操作时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime opTime;

    /** 记录创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
