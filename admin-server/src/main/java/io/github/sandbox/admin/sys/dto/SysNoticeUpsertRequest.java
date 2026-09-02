package io.github.sandbox.admin.sys.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告新增/编辑请求（T-0042，FR-SYS-02）。
 *
 * <p>发布人、发布时间、状态由发布/下线动作单独维护，不经本请求篡改。</p>
 */
@Data
public class SysNoticeUpsertRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    private String content;

    /** 生效时间（可空=发布即生效） */
    private LocalDateTime effectiveTime;

    /** 失效时间（可空=长期有效） */
    private LocalDateTime expireTime;

    /** 是否置顶：1=置顶 0=普通（默认0） */
    private Integer isTop;
}
