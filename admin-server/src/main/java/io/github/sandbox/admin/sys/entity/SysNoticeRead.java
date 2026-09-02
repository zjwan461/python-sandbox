package io.github.sandbox.admin.sys.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 公告用户已读状态实体（表 sys_notice_read，schema/007，T-0040/T-0042；FR-SYS-03）。
 *
 * <p>追加式记录；不存在行=未读。联合唯一 (notice_id, user_id) 幂等。</p>
 */
@Data
@TableName("sys_notice_read")
public class SysNoticeRead implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公告ID */
    private Long noticeId;

    /** 用户ID */
    private Long userId;

    /** 已读时间 */
    private LocalDateTime readTime;

    /** 创建时间（插入时自动填充） */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 创建人 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;
}
