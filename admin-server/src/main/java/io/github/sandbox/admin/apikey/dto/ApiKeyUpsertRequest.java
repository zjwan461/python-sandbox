package io.github.sandbox.admin.apikey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ApiKey 创建/编辑请求（T-0029，FR-APIKEY-01）。
 *
 * <p>不含任何密钥字段——明文由服务端生成、仅一次性返回（默认决策 #1）。
 * 生效/过期时间语义：effectiveTime 为空=立即生效；expireTime 为空=永不过期。</p>
 */
@Data
public class ApiKeyUpsertRequest {

    @NotBlank(message = "ApiKey 名称不能为空")
    @Size(max = 128, message = "名称长度不能超过128")
    private String name;

    @NotNull(message = "必须绑定客户端")
    private Long clientId;

    /** 绑定用户（可空=按客户端维度计；普通用户仅可留空或绑定自身） */
    private Long boundUserId;

    /** 生效时间（可空=立即生效） */
    private LocalDateTime effectiveTime;

    /** 过期时间（可空=永不过期） */
    private LocalDateTime expireTime;

    /** 限流白名单标志（FR-RATELIMIT-05）：1=跳过全部规则 0=正常判定 */
    private Integer rateLimitExempt;

    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
}
