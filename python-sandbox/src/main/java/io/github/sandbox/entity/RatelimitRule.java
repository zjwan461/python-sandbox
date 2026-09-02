package io.github.sandbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 限流规则只读实体（与 cross-cutting/database/schema/003 对齐，T-0024）。
 *
 * <p>python-sandbox 侧仅启动加载与定时拉取，不做写操作；
 * 拉取条件 status=1 AND effective_time<=NOW() AND (expire_time IS NULL OR expire_time>NOW()) AND deleted=0。</p>
 */
@Data
@TableName("ratelimit_rule")
public class RatelimitRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 维度：API_KEY / CLIENT / GLOBAL（GLOBAL 固定 target_id=0） */
    private String dimension;

    /** 目标主键（API_KEY→client_api_key.id；CLIENT→client_app.id；GLOBAL→0） */
    private Long targetId;

    /** 窗口类型：MINUTE / HOUR / DAY */
    private String windowType;

    /** 窗口内最大请求数（正整数） */
    private Integer threshold;

    /** 优先级（数值越小越先判定；多规则叠加任一命中即拒绝） */
    private Integer priority;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    /** 生效时间（NULL=立即生效） */
    private LocalDateTime effectiveTime;

    /** 失效时间（NULL=永不失效） */
    private LocalDateTime expireTime;

    @TableLogic
    private Integer deleted;
}
