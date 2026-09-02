package io.github.sandbox.admin.session.dto;

import io.github.sandbox.admin.log.dto.ApiLogVO;
import io.github.sandbox.admin.log.dto.SandboxLogVO;
import lombok.Data;

import java.util.List;

/**
 * 会话关联日志（T-0037，FR-SESSION-03；design.md §10.3）：
 * 该会话最近若干条 API 日志与沙箱操作日志（各自按时间倒序，默认上限 20 条）。
 *
 * <p>数据权限：底层 api_log / sandbox_operation_log 查询天然经 SELF 行过滤，
 * 普通用户只能看到本人归属的记录；会话本身先经可见域校验（越权即 40001）。</p>
 */
@Data
public class SessionRelatedLogsVO {

    /** 会话快照（可见域校验后的原始详情） */
    private io.github.sandbox.admin.bridge.dto.SandboxSessionVO session;

    /** 最近 API 日志（时间倒序） */
    private List<ApiLogVO> apiLogs;

    /** 最近沙箱操作日志（时间倒序） */
    private List<SandboxLogVO> operationLogs;
}
