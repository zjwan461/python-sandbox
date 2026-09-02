package io.github.sandbox.admin.log.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.result.R;
import io.github.sandbox.admin.common.util.ExportUtil;
import io.github.sandbox.admin.log.dto.ApiLogQuery;
import io.github.sandbox.admin.log.dto.ApiLogVO;
import io.github.sandbox.admin.log.dto.DetectLogQuery;
import io.github.sandbox.admin.log.dto.DetectLogVO;
import io.github.sandbox.admin.log.dto.SandboxLogQuery;
import io.github.sandbox.admin.log.dto.SandboxLogVO;
import io.github.sandbox.admin.log.dto.TraceDetailVO;
import io.github.sandbox.admin.log.service.LogQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 调用记录查询接口（T-0032/T-0045 后端部分，design.md §10.3）。
 *
 * <p>路径口径：{@code /logs/api}、{@code /logs/api/{id}}、{@code /logs/sandbox}、
 * {@code /logs/sandbox/{id}}、{@code /logs/trace/{traceId}}（相对 context-path /admin-api）。
 * 全部只读；数据权限由 api_log / sandbox_operation_log 的 SELF 行过滤保证（T-0021），
 * 管理员与审计员（ALL 域）获得全部记录（FR-RBAC-01）。</p>
 *
 * <p>批次6（T-0045，FR-LOG-06）新增：{@code /logs/api/export} 与
 * {@code /logs/sandbox/export}——按当前筛选与排序口径导出 CSV / Excel（SpreadsheetML），
 * 权限码 apilog:export（种子已登记），导出动作以 module=apilog type=reset 之外的
 * 语义落审计（export）；导出内容保留截断标记列，不含 ApiKey 明文/密码/验证码/内部凭证。</p>
 */
@RestController
@RequestMapping("/logs")
@RequiredArgsConstructor
public class LogQueryController {

    private static final int EXPORT_CAP = 10000;
    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter CELL_TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogQueryService logQueryService;

    /** API 日志分页（FR-LOG-01，默认时间倒序） */
    @SaCheckPermission("apilog:view")
    @GetMapping("/api")
    public R<PageResult<ApiLogVO>> pageApiLog(ApiLogQuery query) {
        return R.ok(logQueryService.pageApiLog(query));
    }

    /** API 日志详情 */
    @SaCheckPermission("apilog:view")
    @GetMapping("/api/{id}")
    public R<ApiLogVO> apiLogDetail(@PathVariable Long id) {
        return R.ok(logQueryService.apiLogDetail(id));
    }

    /** 沙箱操作日志分页（FR-LOG-02） */
    @SaCheckPermission("apilog:view")
    @GetMapping("/sandbox")
    public R<PageResult<SandboxLogVO>> pageSandboxLog(SandboxLogQuery query) {
        return R.ok(logQueryService.pageSandboxLog(query));
    }

    /** 沙箱操作日志详情 */
    @SaCheckPermission("apilog:view")
    @GetMapping("/sandbox/{id}")
    public R<SandboxLogVO> sandboxLogDetail(@PathVariable Long id) {
        return R.ok(logQueryService.sandboxLogDetail(id));
    }

    /** traceId 链路聚合详情（FR-LOG-03：API 日志 + 多次沙箱操作同屏） */
    @SaCheckPermission("apilog:view")
    @GetMapping("/trace/{traceId}")
    public R<TraceDetailVO> traceDetail(@PathVariable String traceId) {
        return R.ok(logQueryService.traceDetail(traceId));
    }

    // ===================== CodeGuard 模型检测记录 =====================

    /** 模型推理检测记录分页（默认时间倒序） */
    @SaCheckPermission("apilog:view")
    @GetMapping("/detect")
    public R<PageResult<DetectLogVO>> pageDetectLog(DetectLogQuery query) {
        return R.ok(logQueryService.pageDetectLog(query));
    }

    /** 模型推理检测记录详情 */
    @SaCheckPermission("apilog:view")
    @GetMapping("/detect/{id}")
    public R<DetectLogVO> detectLogDetail(@PathVariable Long id) {
        return R.ok(logQueryService.detectLogDetail(id));
    }

    /** 模型推理检测记录导出（format=csv|excel，口径与其他日志一致） */
    @SaCheckPermission("apilog:export")
    @OperationLog(module = "apilog", type = "export")
    @GetMapping("/detect/export")
    public ResponseEntity<byte[]> exportDetectLog(DetectLogQuery query,
                                                  @RequestParam(defaultValue = "csv") String format) {
        List<DetectLogVO> rows = logQueryService.listDetectLogForExport(query, EXPORT_CAP);
        List<String> headers = List.of("ID", "traceId", "会话ID", "客户端编码", "ApiKey(掩码)", "归属用户",
                "模型", "判定标签", "危险", "原始输出", "调用状态", "处置", "耗时(ms)",
                "代码长度", "错误信息", "代码片段", "时间");
        List<List<Object>> data = rows.stream().map(v -> List.<Object>of(
                nz(v.getId()), nz(v.getTraceId()), nz(v.getSessionId()), nz(v.getClientCode()),
                nz(v.getApiKeyLabel()), nz(v.getOwnerUserName()), nz(v.getModelName()),
                nz(v.getLabel()), v.getDangerous() == null ? "" : (v.getDangerous() == 1 ? "是" : "否"),
                nz(v.getRawOutput()), nz(v.getDetectStatus()), nz(v.getDecision()),
                nz(v.getLatencyMs()), nz(v.getCodeLength()), nz(v.getErrorMessage()),
                nz(v.getCodeSnippet()),
                v.getCreatedAt() == null ? "" : CELL_TS.format(v.getCreatedAt()))).toList();
        return download("codeguard_detect_log", format, headers, data);
    }

    // ===================== T-0045 导出 =====================

    /**
     * API 日志导出（FR-LOG-06）：format=csv|excel；与当前筛选、排序、数据权限一致；
     * 长内容保留截断标记列（不无提示丢失）；上限 10000 条（超出部分不导出，文件名不受影响）。
     */
    @SaCheckPermission("apilog:export")
    @OperationLog(module = "apilog", type = "export")
    @GetMapping("/api/export")
    public ResponseEntity<byte[]> exportApiLog(ApiLogQuery query,
                                               @RequestParam(defaultValue = "csv") String format) {
        List<ApiLogVO> rows = logQueryService.listApiLogForExport(query, EXPORT_CAP);
        List<String> headers = List.of("ID", "traceId", "会话ID", "客户端编码", "ApiKey(掩码)", "归属用户",
                "HTTP方法", "接口路径", "响应码", "耗时(ms)", "客户端IP", "限流命中", "命中规则ID",
                "请求参数", "请求参数截断", "时间");
        List<List<Object>> data = rows.stream().map(v -> List.<Object>of(
                nz(v.getId()), nz(v.getTraceId()), nz(v.getSessionId()), nz(v.getClientCode()),
                nz(v.getApiKeyLabel()), nz(v.getOwnerUserName()), nz(v.getHttpMethod()), nz(v.getApiPath()),
                nz(v.getResponseCode()), nz(v.getExecutionTime()), nz(v.getClientIp()),
                v.getRateLimitHit() != null && v.getRateLimitHit() == 1 ? "是" : "否",
                nz(v.getRateLimitRuleId()), nz(v.getRequestParams()),
                Boolean.TRUE.equals(v.getRequestParamsTruncated()) ? "已截断" : "完整",
                v.getCreatedAt() == null ? "" : CELL_TS.format(v.getCreatedAt()))).toList();
        return download("api_log", format, headers, data);
    }

    /** 沙箱操作日志导出（FR-LOG-06，口径同上） */
    @SaCheckPermission("apilog:export")
    @OperationLog(module = "apilog", type = "export")
    @GetMapping("/sandbox/export")
    public ResponseEntity<byte[]> exportSandboxLog(SandboxLogQuery query,
                                                   @RequestParam(defaultValue = "csv") String format) {
        List<SandboxLogVO> rows = logQueryService.listSandboxLogForExport(query, EXPORT_CAP);
        List<String> headers = List.of("ID", "traceId", "会话ID", "操作类型", "客户端编码", "ApiKey(掩码)", "归属用户",
                "结果", "退出码", "耗时(ms)", "标准输出", "stdout截断", "标准错误", "stderr截断",
                "错误信息", "操作内容截断", "操作内容", "时间");
        List<List<Object>> data = rows.stream().map(v -> List.<Object>of(
                nz(v.getId()), nz(v.getTraceId()), nz(v.getSessionId()), nz(v.getOperationType()),
                nz(v.getClientCode()), nz(v.getApiKeyLabel()), nz(v.getOwnerUserName()),
                nz(v.getResult()), nz(v.getExitCode()), nz(v.getExecutionTime()),
                nz(v.getStdout()), Boolean.TRUE.equals(v.getStdoutTruncated()) ? "已截断" : "完整",
                nz(v.getStderr()), Boolean.TRUE.equals(v.getStderrTruncated()) ? "已截断" : "完整",
                nz(v.getErrorMessage()),
                Boolean.TRUE.equals(v.getOperationContentTruncated()) ? "已截断" : "完整",
                nz(v.getOperationContent()),
                v.getCreatedAt() == null ? "" : CELL_TS.format(v.getCreatedAt()))).toList();
        return download("sandbox_op_log", format, headers, data);
    }

    // ===================== internal =====================

    private ResponseEntity<byte[]> download(String baseName, String format,
                                            List<String> headers, List<List<Object>> data) {
        boolean excel = "excel".equalsIgnoreCase(format) || "xls".equalsIgnoreCase(format);
        byte[] body = excel
                ? ExportUtil.toExcelXml(baseName, headers, data)
                : ExportUtil.toCsv(headers, data);
        String fileName = baseName + "_" + LocalDateTime.now().format(FILE_TS) + (excel ? ".xls" : ".csv");
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName
                        + "\"; filename*=UTF-8''" + encoded)
                .contentType(excel
                        ? MediaType.parseMediaType("application/vnd.ms-excel;charset=UTF-8")
                        : MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    private Object nz(Object v) {
        return v == null ? "" : v;
    }
}
