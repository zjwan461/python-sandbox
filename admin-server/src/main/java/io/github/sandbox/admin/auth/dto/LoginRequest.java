package io.github.sandbox.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求（T-0015）。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 64, message = "用户名长度不能超过64")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 128, message = "密码长度不能超过128")
    private String password;

    /** 验证码标识（本批次策略：始终要求验证码） */
    @NotBlank(message = "验证码标识不能为空")
    private String captchaId;

    /** 用户输入的验证码答案 */
    @NotBlank(message = "验证码不能为空")
    @Size(max = 10, message = "验证码长度非法")
    private String captchaAnswer;

    /** 记住我（T-0034，FR-AUTH-03）：true 时签发长期 token 至 HttpOnly Cookie；默认不签发 */
    private boolean rememberMe;
}
