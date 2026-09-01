package io.github.sandbox.admin.rbac.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 用户新增/编辑请求（T-0018）。
 */
@Data
public class UserUpsertRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度需在3-64之间")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "用户名仅允许字母、数字、下划线与中划线")
    private String username;

    @Size(max = 64)
    private String nickname;

    @Email(message = "邮箱格式非法")
    @Size(max = 128)
    private String email;

    @Size(max = 32)
    private String phone;

    @Size(max = 255)
    private String avatar;

    /** 新增时必填（编辑时忽略，密码走独立重置通道） */
    @Size(max = 128)
    private String password;

    /** 状态：1=启用 0=停用，默认启用 */
    private Integer status;

    /** 所属部门（可空文本） */
    @Size(max = 128)
    private String deptName;

    @Size(max = 255)
    private String remark;

    /** 绑定角色ID列表（至少一个） */
    private List<Long> roleIds;
}
