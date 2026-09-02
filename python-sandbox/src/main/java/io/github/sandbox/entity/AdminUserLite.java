package io.github.sandbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 管理端用户状态只读视图（仅认证所需最小字段集，T-0023 USER_DISABLED 语义）。
 *
 * <p>映射 {@code admin_user} 表；python-sandbox 不复制 admin-server 的实体定义，
 * 仅声明本侧鉴权用到的列。</p>
 */
@Data
@TableName("admin_user")
public class AdminUserLite {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 状态：1=启用 0=停用 */
    private Integer status;

    @TableLogic
    private Integer deleted;
}
