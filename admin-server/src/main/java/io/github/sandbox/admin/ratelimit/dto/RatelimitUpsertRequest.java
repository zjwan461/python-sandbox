package io.github.sandbox.admin.ratelimit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 限流规则新增/编辑请求（T-0030，FR-RATELIMIT-01）。
 *
 * <p>维度与目标一致性、目标存在性、有效期先后等业务校验在 Service 层完成。</p>
 */
@Data
public class RatelimitUpsertRequest {

    /** 维度：API_KEY / CLIENT（GLOBAL 全局默认规则仅管理员，T-0036 扩展） */
    @NotBlank(message = "维度不能为空")
    private String dimension;

    /** 目标主键（API_KEY→client_api_key.id；CLIENT→client_app.id） */
    @NotNull(message = "目标不能为空")
    private Long targetId;

    /** 窗口类型：MINUTE / HOUR / DAY */
    @NotBlank(message = "窗口类型不能为空")
    private String windowType;

    /** 窗口内最大请求数（正整数） */
    @NotNull(message = "阈值不能为空")
    @Min(value = 1, message = "阈值必须为正整数")
    @Max(value = 1_000_000_000, message = "阈值超出允许范围")
    private Integer threshold;

    /** 优先级（数值越小越先判定，默认 100） */
    @Min(value = 1, message = "优先级最小为 1")
    private Integer priority;

    /** 状态：1=启用 0=停用（默认 1） */
    private Integer status;

    /** 生效时间（可空=立即生效） */
    private LocalDateTime effectiveTime;

    /** 失效时间（可空=永不失效） */
    private LocalDateTime expireTime;

    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;
}
