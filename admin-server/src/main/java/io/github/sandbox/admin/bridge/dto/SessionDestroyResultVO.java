package io.github.sandbox.admin.bridge.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 会话强销回执（T-0031，design.md §8.4 / §11.4，默认决策 #7）。
 *
 * <p>回执含 成功/失败 + 剩余会话数；失败结果必须原样回传前端，不得虚构成功。</p>
 */
@Data
public class SessionDestroyResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否销毁成功 */
    private boolean success;

    /** 结果描述（成功或失败原因） */
    private String message;

    /** 剩余活跃会话数 */
    private Integer remainingSessions;

    /** 被销毁的 sessionId（管理端回填） */
    private String sessionId;
}
