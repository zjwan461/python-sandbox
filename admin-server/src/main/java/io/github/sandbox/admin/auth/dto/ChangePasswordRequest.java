package io.github.sandbox.admin.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改自身密码请求（T-0017）：校验旧密码。
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "旧密码不能为空")
    @Size(max = 128)
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 128, message = "新密码长度需在8-128之间")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    @Size(max = 128)
    private String confirmPassword;
}
