package io.github.sandbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 客户端应用只读实体（与 cross-cutting/database/schema/002 对齐，T-0023）。
 *
 * <p>python-sandbox 侧仅读取认证与归属所需字段，不做写操作；
 * 不复制 admin-server 的实体或配置。</p>
 */
@Data
@TableName("client_app")
public class ClientApp {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 客户端编码（全局唯一） */
    private String clientCode;

    /** 客户端名称 */
    private String clientName;

    /** 归属用户（admin_user.id，可空） */
    private Long ownerUserId;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    @TableLogic
    private Integer deleted;
}
