package io.github.sandbox.admin.session.dto;

import lombok.Data;

/**
 * 活跃会话筛选条件（T-0031，FR-SESSION-01；T-0037 扩展更多维度）。
 *
 * <p>会话数据来自 python-sandbox 内存快照（Bridge 拉取），管理端在内存中过滤，
 * 因此不支持数据库分页语义——分页字段仅作展示层约定，total 以过滤后的实际数量表达。</p>
 */
@Data
public class SessionQuery {

    /** 客户端ID（精确） */
    private Long clientId;

    /** ApiKey ID（精确） */
    private Long apiKeyId;

    /** 归属用户ID（精确；SELF 域另行强制为当前用户） */
    private Long ownerUserId;

    /** 会话ID（模糊；T-0037） */
    private String sessionId;

    /** 会话创建时间下界（含，字符串 yyyy-MM-dd HH:mm:ss；T-0037） */
    private String createdBegin;

    /** 会话创建时间上界（含，字符串 yyyy-MM-dd HH:mm:ss；T-0037） */
    private String createdEnd;

    /** 仅默认会话（true）/ 排除默认会话（false）/ 全部（null） */
    private Boolean isDefault;

    /** 最后活跃时间早于该分钟数即视为不活跃（如 30=最近30分钟无活跃；null=不过滤）。格式 yyyy-MM-dd HH:mm:ss 字符串比较，与 python-sandbox 透传口径一致 */
    private Integer inactiveMinutes;

    /** 页码（1起） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 20;
}
