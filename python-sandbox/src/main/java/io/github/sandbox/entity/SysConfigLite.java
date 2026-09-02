package io.github.sandbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统设置KV只读视图（仅 python-sandbox 侧受控键读取，T-0024/T-0023）。
 *
 * <p>本侧只读取白名单键：ratelimit.anonymous.allowed（匿名灰度）、
 * ratelimit.default.minute/hour/day（全局默认限流）。敏感凭证不从此表读取。</p>
 */
@Data
@TableName("sys_config")
public class SysConfigLite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 稳定设置键 */
    private String configKey;

    /** 设置值（字符串存储） */
    private String configValue;

    /** 值类型：STRING / NUMBER / BOOLEAN / JSON */
    private String valueType;

    @TableLogic
    private Integer deleted;
}
