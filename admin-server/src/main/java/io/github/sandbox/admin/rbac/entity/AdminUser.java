package io.github.sandbox.admin.rbac.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.sandbox.admin.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 管理端用户实体（表 admin_user，字段与 schema/001 严格一致）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("admin_user")
public class AdminUser extends BaseEntity {

    /** 登录用户名（全局唯一） */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 邮箱 */
    private String email;

    /** 手机号 */
    private String phone;

    /** 头像URL */
    private String avatar;

    /** BCrypt 密码哈希（不存明文） */
    private String passwordHash;

    /** 盐（BCrypt 方案下可为空） */
    private String salt;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    /** 所属部门（可空文本字段） */
    private String deptName;

    /** 连续登录失败次数 */
    private Integer loginFailCount;

    /** 锁定到期时间（NULL 或早于当前时间=未锁定） */
    private LocalDateTime lockExpireTime;

    /** 是否首次登录（强制改密）：1=是 0=否 */
    private Integer firstLogin;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 备注 */
    private String remark;
}
