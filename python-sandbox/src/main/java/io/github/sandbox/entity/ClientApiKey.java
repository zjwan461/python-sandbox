package io.github.sandbox.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端 ApiKey 只读实体（与 cross-cutting/database/schema/002 对齐，T-0023）。
 *
 * <p>认证唯一依据为 {@code keyHash}（SHA-256 hex 小写）；python-sandbox 不接触明文，
 * 明文仅由请求 Header 携带后即刻哈希比对。</p>
 */
@Data
@TableName("client_api_key")
public class ClientApiKey {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** ApiKey 名称（人读） */
    private String name;

    /** 绑定客户端（client_app.id） */
    private Long clientId;

    /** 绑定用户（admin_user.id，可空=按客户端维度计） */
    private Long boundUserId;

    /** 明文 SHA-256 摘要（hex 小写 64 字符），认证唯一依据 */
    private String keyHash;

    /** 生效时间（NULL=立即生效） */
    private LocalDateTime effectiveTime;

    /** 过期时间（NULL=永不过期） */
    private LocalDateTime expireTime;

    /** 状态：1=启用 2=停用 3=已过期 4=已撤销（撤销不可逆） */
    private Integer status;

    /** 限流白名单：1=跳过全部规则 0=正常判定 */
    private Integer rateLimitExempt;

    @TableLogic
    private Integer deleted;
}
