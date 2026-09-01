package io.github.sandbox.admin.auth.dto;

import io.github.sandbox.admin.common.security.AdminLoginUser;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 当前用户信息响应（T-0015）：用户 + 角色 + 按钮权限码集合。
 */
@Data
public class WhoamiVO {

    private Long userId;
    private String username;
    private String nickname;

    /** 角色权限字符集合 */
    private List<String> roles;

    /** 按钮/接口权限码集合（驱动前端 v-permission） */
    private Set<String> permissions;

    /** 是否首次登录（强制改密中） */
    private boolean firstLogin;

    /** 数据权限范围：ALL / SELF */
    private String dataScope;

    public static WhoamiVO from(AdminLoginUser u) {
        WhoamiVO vo = new WhoamiVO();
        vo.setUserId(u.getUserId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setRoles(u.getRoles());
        vo.setPermissions(u.getPermissions());
        vo.setFirstLogin(u.isFirstLogin());
        vo.setDataScope(u.getDataScope());
        return vo;
    }
}
