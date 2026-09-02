package io.github.sandbox.admin.log.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * traceId 链路聚合详情（T-0032，FR-LOG-03：一次请求 → 多次沙箱操作同屏）。
 *
 * <p>聚合 api_log 与 sandbox_operation_log 中相同 traceId 的记录；
 * 每条记录均已带截断标记与数据权限过滤。</p>
 */
@Data
public class TraceDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 链路追踪ID */
    private String traceId;

    /** 该 traceId 下的 API 调用日志（通常 1 条，重试等场景可多条） */
    private List<ApiLogVO> apiLogs;

    /** 该 traceId 下的沙箱操作日志（按创建时间升序，呈现执行步骤顺序） */
    private List<SandboxLogVO> operationLogs;
}
