package io.github.sandbox.admin.bridge.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * python-sandbox 活跃会话快照 VO（T-0031，design.md §8.4）。
 *
 * <p>字段与内部接口 {@code GET /internal/sandbox/sessions} 返回口径对齐：
 * sessionId / containerId / containerName / createTime / lastActiveTime / isDefault
 * 与归属键 ownerClientId / ownerApiKeyId / ownerUserId。</p>
 *
 * <p>时间以字符串透传（python-sandbox 侧统一 {@code yyyy-MM-dd HH:mm:ss} 序列化），
 * 管理端不重新解析，避免跨工程时间语义漂移。</p>
 */
@Data
public class SandboxSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID（python-sandbox 内存会话键） */
    private String sessionId;

    /** 容器ID */
    private String containerId;

    /** 容器名（{prefix}{sessionId} 规则） */
    private String containerName;

    /** 创建时间（字符串透传） */
    private String createTime;

    /** 最后活跃时间（字符串透传） */
    private String lastActiveTime;

    /** 是否默认会话 */
    private Boolean isDefault;

    /** 归属客户端（client_app.id，可空） */
    private Long ownerClientId;

    /** 归属 ApiKey（client_api_key.id，可空） */
    private Long ownerApiKeyId;

    /** 归属用户（admin_user.id，可空） */
    private Long ownerUserId;

    /** 归属客户端编码（管理端富化字段，非 python-sandbox 返回） */
    private String ownerClientCode;

    /** 归属 ApiKey 名称（管理端富化字段） */
    private String ownerApiKeyLabel;

    /** 归属用户名（管理端富化字段） */
    private String ownerUserName;
}
