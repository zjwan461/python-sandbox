package io.github.sandbox.admin.rbac.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户列表/详情视图对象（T-0018）。不含密码哈希等敏感字段。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String phone;
    private String avatar;
    private Integer status;
    private String deptName;

    /** 是否处于锁定状态（由 lock_expire_time 派生的展示口径） */
    private Boolean locked;

    /** 锁定到期时间（locked=true 时有值） */
    private LocalDateTime lockExpireTime;

    /** 是否首次登录（待强制改密） */
    private Integer firstLogin;

    private LocalDateTime lastLoginTime;
    private String remark;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;

    /** 已绑定角色（id + name + key） */
    private List<RoleBrief> roles;

    @Data
    public static class RoleBrief {
        private Long id;
        private String roleName;
        private String roleKey;
    }
}
