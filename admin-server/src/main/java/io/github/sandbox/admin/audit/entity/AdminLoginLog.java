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
 * 管理端登录日志实体（表 admin_login_log，字段与 schema/004 严格一致）。
 *
 * <p>只追加口径：不含 update/deleted 字段，不继承 BaseEntity。</p>
 */
@Data
@TableName("admin_login_log")
public class AdminLoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 用户ID（成功或可识别账号时填充） */
    private Long userId;

    /** 登录方式：PASSWORD / REMEMBER_ME / INTERNAL */
    private String loginType;

    /** 结果：SUCCESS / FAIL / LOCKED */
    private String result;

    /** 失败/锁定原因：BAD_CREDENTIALS / CAPTCHA_ERROR / ACCOUNT_DISABLED / ACCOUNT_LOCKED 等 */
    private String failReason;

    /** 来源IP */
    private String ip;

    /** 浏览器UA */
    private String userAgent;

    /** 发生时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime loginTime;

    /** 记录创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
