package io.github.sandbox.admin.sys.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告展示 VO（T-0042，FR-SYS-03）。
 *
 * <p>面向"当前用户"投递视角：{@code read} 表达该用户是否已读；
 * {@code top} 表达置顶。管理端列表同样使用该结构（read 对管理员亦按自身已读计算）。</p>
 */
@Data
public class SysNoticeVO {

    private Long id;
    private String title;
    private String content;
    private LocalDateTime effectiveTime;
    private LocalDateTime expireTime;
    private boolean top;
    private Integer status;
    private String publisherName;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;

    /** 当前登录用户是否已读 */
    private boolean read;

    /** 是否处于有效展示窗口（生效<=now 且 未失效）；用于前端区分"已失效"标注 */
    private boolean inWindow;
}
