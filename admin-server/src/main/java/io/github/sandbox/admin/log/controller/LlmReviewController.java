package io.github.sandbox.admin.log.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.sandbox.admin.audit.annotation.OperationLog;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.result.R;
import io.github.sandbox.admin.log.dto.HumanReviewRequest;
import io.github.sandbox.admin.log.dto.LlmReviewQuery;
import io.github.sandbox.admin.log.dto.LlmReviewVO;
import io.github.sandbox.admin.log.service.LlmReviewService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 大模型复检任务管理接口。
 *
 * <p>路径：{@code /logs/llm-review/**}，权限码 {@code llmreview:view / llmreview:edit / llmreview:export}。</p>
 */
@Slf4j
@RestController
@RequestMapping("/logs/llm-review")
@RequiredArgsConstructor
public class LlmReviewController {

    private final LlmReviewService llmReviewService;

    /** 复检任务分页 */
    @SaCheckPermission("llmreview:view")
    @GetMapping
    public R<PageResult<LlmReviewVO>> pageReviewTasks(LlmReviewQuery query) {
        Page<LlmReviewVO> page = llmReviewService.pageReviewTasks(query);
        return R.ok(new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize()));
    }

    /** 复检任务详情 */
    @SaCheckPermission("llmreview:view")
    @GetMapping("/{id}")
    public R<LlmReviewVO> reviewTaskDetail(@PathVariable Long id) {
        return R.ok(llmReviewService.getReviewTaskDetail(id));
    }

    /** 创建复检任务（从检测记录触发） */
    @SaCheckPermission("llmreview:edit")
    @OperationLog(module = "llmreview", type = "create")
    @PostMapping
    public R<Long> createReviewTask(@RequestParam Long detectLogId) {
        return R.ok(llmReviewService.createReviewTask(detectLogId));
    }

    /** 批量创建复检任务 */
    @SaCheckPermission("llmreview:edit")
    @OperationLog(module = "llmreview", type = "batchCreate")
    @PostMapping("/batch")
    public R<Map<String, Object>> batchCreateReviewTask(@RequestBody List<Long> detectLogIds) {
        return R.ok(llmReviewService.batchCreateReviewTask(detectLogIds));
    }

    /** 人工复核 */
    @SaCheckPermission("llmreview:edit")
    @OperationLog(module = "llmreview", type = "review")
    @PutMapping("/{id}/review")
    public R<Void> humanReview(@PathVariable Long id, @Valid @RequestBody HumanReviewRequest request) {
        llmReviewService.humanReview(id, request);
        return R.ok();
    }

    /** 取消复检任务 */
    @SaCheckPermission("llmreview:edit")
    @OperationLog(module = "llmreview", type = "cancel")
    @PutMapping("/{id}/cancel")
    public R<Void> cancelReviewTask(@PathVariable Long id) {
        llmReviewService.cancelReviewTask(id);
        return R.ok();
    }

    /** 立即执行复检任务（手动触发，跳过定时调度） */
    @SaCheckPermission("llmreview:edit")
    @OperationLog(module = "llmreview", type = "execute")
    @PostMapping("/{id}/execute")
    public R<Void> executeReviewTask(@PathVariable Long id) {
        llmReviewService.executeReviewTask(id);
        return R.ok();
    }

    /**
     * 导出复检结果为 JSONL 格式（用于模型微调）。
     *
     * <p>每行一个 JSON 对象，包含：code_snippet / small_model_label / llm_label /
     * llm_explanation / human_label / agreed 等字段。</p>
     */
    @SaCheckPermission("llmreview:export")
    @OperationLog(module = "llmreview", type = "export")
    @GetMapping("/export/jsonl")
    public void exportJsonl(LlmReviewQuery query, HttpServletResponse response) {
        // 限制导出量
        query.setPageSize(10000);
        Page<LlmReviewVO> page = llmReviewService.pageReviewTasks(query);
        List<LlmReviewVO> records = page.getRecords();

        String fileName = "llm_review_export.jsonl";
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

        response.setContentType("application/x-ndjson;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encoded);
        response.setCharacterEncoding("UTF-8");

        try (PrintWriter writer = response.getWriter()) {
            for (LlmReviewVO vo : records) {
                if (!"SUCCESS".equals(vo.getTaskStatus())) {
                    continue;
                }
                String json = buildJsonlLine(vo);
                writer.println(json);
            }
            writer.flush();
        } catch (Exception e) {
            log.error("导出 JSONL 失败", e);
        }
    }

    /**
     * 构建单行 JSONL（手动拼接，避免引入额外 JSON 库依赖）。
     */
    private String buildJsonlLine(LlmReviewVO vo) {
        StringBuilder sb = new StringBuilder("{");
        appendJsonField(sb, "code_snippet", vo.getCodeSnippet(), true);
        appendJsonField(sb, "small_model_label", vo.getSmallModelLabel(), false);
        appendJsonField(sb, "small_model_raw_output", vo.getSmallModelRawOutput(), false);
        appendJsonField(sb, "llm_provider", vo.getLlmProvider(), false);
        appendJsonField(sb, "llm_model", vo.getLlmModel(), false);
        appendJsonField(sb, "llm_label", vo.getLlmLabel(), false);
        appendJsonField(sb, "llm_explanation", vo.getLlmExplanation(), false);
        appendJsonField(sb, "human_review_status", vo.getHumanReviewStatus(), false);
        appendJsonField(sb, "human_label", vo.getHumanLabel(), false);
        appendJsonField(sb, "human_remark", vo.getHumanRemark(), false);
        // 最终标签：人工 > 大模型
        String finalLabel = vo.getHumanLabel() != null ? vo.getHumanLabel() : vo.getLlmLabel();
        appendJsonField(sb, "final_label", finalLabel, false);
        // agreed：小模型与大模型/人工是否一致
        boolean agreed = finalLabel != null && finalLabel.equals(vo.getSmallModelLabel());
        sb.append("\"agreed\":").append(agreed);
        sb.append("}");
        return sb.toString();
    }

    private void appendJsonField(StringBuilder sb, String key, String value, boolean first) {
        if (!first) {
            sb.append(",");
        }
        sb.append("\"").append(key).append("\":");
        if (value == null) {
            sb.append("null");
        } else {
            sb.append("\"").append(escapeJson(value)).append("\"");
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
