package io.github.sandbox.admin.apikey.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * ApiKey 列表/详情视图（T-0029，FR-APIKEY-02/03/07）。
 *
 * <p>安全口径：永不包含明文与 keyHash；识别仅依赖 keyPrefix + keySuffixMask。
 * 客户端/绑定用户名为管理端富化字段。</p>
 */
@Data
public class ApiKeyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** ApiKey 名称 */
    private String name;

    private Long clientId;

    /** 客户端编码（富化） */
    private String clientCode;

    /** 客户端名称（富化） */
    private String clientName;

    private Long boundUserId;

    /** 绑定用户名（富化，可空） */
    private String boundUserName;

    /** 密钥前缀（如 sk_live_a1b2） */
    private String keyPrefix;

    /** 后 4 位掩码 */
    private String keySuffixMask;

    private LocalDateTime effectiveTime;

    private LocalDateTime expireTime;

    /** 状态：1=启用 2=停用 3=已过期 4=已撤销 */
    private Integer status;

    /** 状态中文标签（派生） */
    private String statusLabel;

    /** 限流白名单：1=跳过全部规则 */
    private Integer rateLimitExempt;

    /** 一次性明文是否尚未消费（仅用于前端"是否还能重新生成入口提示"，明文本体不在此返回） */
    private Integer plaintextOneShot;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
