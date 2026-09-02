package io.github.sandbox.admin.ratelimit.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.sandbox.admin.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 限流规则实体（表 ratelimit_rule，字段与 cross-cutting/database/schema/003 严格一致）。
 *
 * <p>该表为系统级元数据表，不在数据权限 SELF 注册表内（T-0021 验收：元数据表
 * 不被行过滤误作用）；归属约束在 Service 层对普通用户显式校验
 * （目标 ApiKey/客户端必须在其可见域内）。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ratelimit_rule")
public class RatelimitRule extends BaseEntity {

    /** 维度：API_KEY / CLIENT / GLOBAL */
    private String dimension;

    /** 目标主键（API_KEY→client_api_key.id；CLIENT→client_app.id；GLOBAL→固定0） */
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

    /** 备注 */
    private String remark;

    // ===== 维度常量 =====
    public static final String DIM_API_KEY = "API_KEY";
    public static final String DIM_CLIENT = "CLIENT";
    public static final String DIM_GLOBAL = "GLOBAL";

    // ===== 窗口常量 =====
    public static final String WINDOW_MINUTE = "MINUTE";
    public static final String WINDOW_HOUR = "HOUR";
    public static final String WINDOW_DAY = "DAY";
}
