package io.github.sandbox.admin.session.dto;

import lombok.Data;

import java.util.List;

/**
 * 无活跃会话批量清理请求（T-0044，FR-SESSION-06；design.md §8.4）。
 *
 * <p>两种目标集合口径（二选一，sessionIds 优先）：</p>
 * <ul>
 *   <li>{@code inactiveMinutes}：按"最后活跃时间早于 N 分钟"自动筛选目标集合
 *       （默认会话不被隐式纳入——自动筛选结果强制排除默认会话，验收口径）。</li>
 *   <li>{@code sessionIds}：前端批量选择后的显式目标清单（含默认会话时前端已
 *       高危显式二次确认；后端仍逐项回执，任一失败不虚构全部成功）。</li>
 * </ul>
 */
@Data
public class SessionBatchDestroyRequest {

    /** 不活跃阈值（分钟）；与 sessionIds 至少提供其一 */
    private Integer inactiveMinutes;

    /** 显式选择的目标会话ID清单（可空） */
    private List<String> sessionIds;
}
