package io.github.sandbox.admin.auth.dto;

import lombok.Data;

/**
 * 登录成功响应（T-0015）。
 *
 * <p>短期 token 由前端保存于 Pinia（不写入可被脚本读取的其他本地存储，FR-AUTH 口径）；
 * 首次登录强制改密标记透出，前端据此强制跳转改密页。</p>
 */
@Data
public class LoginResultVO {

    /** Sa-Token 短期 token 值 */
    private String token;

    /** token 有效期（秒） */
    private long tokenTimeout;

    /** 是否首次登录（强制改密，不能跳过） */
    private boolean firstLogin;

    /** 用户ID */
    private Long userId;

    /** 用户名 */
    private String username;
}
