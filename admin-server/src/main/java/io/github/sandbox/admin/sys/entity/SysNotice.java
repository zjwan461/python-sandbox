package io.github.sandbox.admin.sys.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.sandbox.admin.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通知公告实体（表 sys_notice，schema/007，T-0040/T-0042；FR-SYS-02）。
 *
 * <p>公告与操作审计/登录日志严格分离（T-0040 验收）；发布动作回填 publisher 与 publish_time。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_notice")
public class SysNotice extends BaseEntity {

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 生效时间（NULL=发布即生效） */
    private LocalDateTime effectiveTime;

    /** 失效时间（NULL=长期有效） */
    private LocalDateTime expireTime;

    /** 是否置顶：1=置顶 0=普通 */
    private Integer isTop;

    /** 状态：0=草稿 1=已发布 */
    private Integer status;

    /** 发布人（admin_user.id） */
    private Long publisherId;

    /** 发布人用户名（冗余展示） */
    private String publisherName;

    /** 发布时间 */
    private LocalDateTime publishTime;

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
}
