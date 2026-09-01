package io.github.sandbox.admin.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端业务实体公共基类（T-0009，design.md §4.4）。
 *
 * <p>公共字段口径与 {@code cross-cutting/database/schema/*.sql} 严格一致：
 * {@code id / create_time / update_time / create_by / update_by / deleted}。</p>
 *
 * <ul>
 *   <li>主键策略：{@code IdType.AUTO}（与 schema BIGINT AUTO_INCREMENT 对齐，全工程统一）。</li>
 *   <li>{@code create_time / update_time / create_by / update_by} 由
 *       {@code AdminMetaObjectHandler} 自动填充；登录前操作 create_by 口径为 {@code system}。</li>
 *   <li>{@code deleted} 为逻辑删除标志（0=正常 1=已删除），全局配置驱动，查询自动排除。</li>
 * </ul>
 *
 * <p>注意：审计两表（admin_login_log / admin_op_log）为"只追加"口径，不继承本类；
 * 联合主键关联表（admin_user_role / admin_role_menu）亦不继承。</p>
 */
@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键（BIGINT AUTO_INCREMENT，与 schema 对齐） */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 创建时间（插入时自动填充） */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间（插入与更新时自动填充） */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建人（登录前/种子 = system） */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    private String createBy;

    /** 更新人（登录前/系统操作 = system） */
    @TableField(value = "update_by", fill = FieldFill.INSERT_UPDATE)
    private String updateBy;

    /** 逻辑删除标志：0=正常 1=已删除（查询不回传，仅用于框架改写） */
    @TableLogic
    @TableField(value = "deleted", select = false)
    private Integer deleted;
}
