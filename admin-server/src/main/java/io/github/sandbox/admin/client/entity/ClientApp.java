package io.github.sandbox.admin.client.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.sandbox.admin.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 客户端应用实体（表 client_app，字段与 cross-cutting/database/schema/002 严格一致）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("client_app")
public class ClientApp extends BaseEntity {

    /** 客户端编码（全局唯一，对外可见标识） */
    private String clientCode;

    /** 客户端名称（可重复，人读） */
    private String clientName;

    /** 描述 */
    private String description;

    /** 归属用户（admin_user.id，可空=按客户端维度计，默认决策 #4/#5） */
    private Long ownerUserId;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    /** 备注 */
    private String remark;
}
