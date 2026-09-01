package io.github.sandbox.admin.sys.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.sandbox.admin.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统设置 KV 实体（表 sys_config，字段与 schema/005 严格一致）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_config")
public class SysConfig extends BaseEntity {

    /** 稳定设置键（全局唯一，如 login.fail.threshold） */
    private String configKey;

    /** 设置值（字符串存储，按 valueType 校验） */
    private String configValue;

    /** 值类型：STRING / NUMBER / BOOLEAN / JSON */
    private String valueType;

    /** 设置项显示名称 */
    private String configName;

    /** 说明 */
    private String description;

    /** 是否内置键：1=内置不可删 0=自定义（列 is_built_in，驼峰转换自动对齐） */
    private Integer isBuiltIn;
}
