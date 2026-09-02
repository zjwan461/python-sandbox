package io.github.sandbox.admin.apikey.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.github.sandbox.admin.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 客户端 ApiKey 实体（表 client_api_key，字段与 cross-cutting/database/schema/002 严格一致）。
 *
 * <p>安全硬约束（默认决策 #1）：本实体不包含任何可恢复明文字段。
 * {@code keyHash} 为 SHA-256(hex) 不可逆摘要，仅作 python-sandbox 侧查表认证依据；
 * 任何列表、详情、日志与 Redis 均不得透出。{@code plaintextOneShot=1} 表示
 * "刚创建尚未消费"，明文只在创建响应的"生成瞬间"存在一次。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("client_api_key")
public class ClientApiKey extends BaseEntity {

    /** ApiKey 名称（人读） */
    private String name;

    /** 绑定客户端（client_app.id，必填） */
    private Long clientId;

    /** 绑定用户（admin_user.id，可空=按客户端维度计） */
    private Long boundUserId;

    /** SHA-256 摘要（hex 小写 64 字符；认证唯一依据，禁止透出到任何响应/日志） */
    private String keyHash;

    /** 密钥前缀（如 sk_live_ab12），外部识别用 */
    private String keyPrefix;

    /** 后 4 位掩码，界面识别用 */
    private String keySuffixMask;

    /** 生效时间（NULL=立即生效） */
    private LocalDateTime effectiveTime;

    /** 过期时间（NULL=永不过期） */
    private LocalDateTime expireTime;

    /** 状态：1=启用 2=停用 3=已过期 4=已撤销（撤销不可逆） */
    private Integer status;

    /** 限流白名单（FR-RATELIMIT-05）：1=跳过全部规则 0=正常判定 */
    private Integer rateLimitExempt;

    /** 一次性明文展示消费标记：1=可展示一次 0=已消费（列表与详情永不返回明文） */
    private Integer plaintextOneShot;

    /** 备注 */
    private String remark;

    // ===== 状态常量 =====
    public static final int STATUS_ENABLED = 1;
    public static final int STATUS_DISABLED = 2;
    public static final int STATUS_EXPIRED = 3;
    public static final int STATUS_REVOKED = 4;
}
