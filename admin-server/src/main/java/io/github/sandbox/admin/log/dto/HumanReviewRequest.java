package io.github.sandbox.admin.log.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 人工复核请求。
 */
@Data
public class HumanReviewRequest {

    /** 复核状态：AGREED=同意大模型 / DISAGREED=不同意大模型 */
    @NotBlank(message = "复核状态不能为空")
    private String humanReviewStatus;

    /** 人工最终判定标签：SAFE / DANGEROUS（DISAGREED 时必填） */
    private String humanLabel;

    /** 人工复核备注 */
    private String humanRemark;
}
