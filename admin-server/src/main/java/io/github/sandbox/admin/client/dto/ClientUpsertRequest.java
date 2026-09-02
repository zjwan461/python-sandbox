package io.github.sandbox.admin.client.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客户端新增/编辑请求（T-0028，FR-CLIENT-02）。
 *
 * <p>归属用户可空（默认决策 #4/#5）；普通用户提交时归属用户被强制置为自身（见 Service）。</p>
 */
@Data
public class ClientUpsertRequest {

    @NotBlank(message = "客户端编码不能为空")
    @Size(max = 64, message = "客户端编码长度不能超过64")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "客户端编码仅允许字母、数字、下划线与连字符")
    private String clientCode;

    @NotBlank(message = "客户端名称不能为空")
    @Size(max = 128, message = "客户端名称长度不能超过128")
    private String clientName;

    @Size(max = 255, message = "描述长度不能超过255")
    private String description;

    /** 归属用户（可空=按客户端维度计） */
    private Long ownerUserId;

    /** 状态：1=启用 0=停用（新增默认 1；启停走独立接口以保证审计语义） */
    private Integer status;

    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
}
