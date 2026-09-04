package io.github.sandbox.admin.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.security.SecurityUtils;
import io.github.sandbox.admin.log.dto.HumanReviewRequest;
import io.github.sandbox.admin.log.dto.LlmReviewQuery;
import io.github.sandbox.admin.log.dto.LlmReviewVO;
import io.github.sandbox.admin.log.entity.CodeGuardDetectLogView;
import io.github.sandbox.admin.log.entity.LlmReviewTask;
import io.github.sandbox.admin.log.mapper.CodeGuardDetectLogViewMapper;
import io.github.sandbox.admin.log.mapper.LlmReviewTaskMapper;
import io.github.sandbox.admin.rbac.entity.AdminUser;
import io.github.sandbox.admin.rbac.mapper.AdminUserMapper;
import io.github.sandbox.admin.sys.service.SysConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 大模型复检任务服务。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>创建复检任务（从检测记录触发）</li>
 *   <li>异步执行复检（调用大模型 API）</li>
 *   <li>人工复核（覆盖大模型结果）</li>
 *   <li>查询与分页</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmReviewService {

    private final LlmReviewTaskMapper llmReviewTaskMapper;
    private final CodeGuardDetectLogViewMapper detectLogMapper;
    private final AdminUserMapper adminUserMapper;
    private final LlmClientService llmClientService;
    private final SysConfigReader sysConfigReader;

    /**
     * 创建复检任务。
     *
     * @param detectLogId 检测记录 ID
     * @return 任务 ID
     */
    @Transactional
    public Long createReviewTask(Long detectLogId) {
        // 检查是否已存在任务
        LambdaQueryWrapper<LlmReviewTask> wrapper = Wrappers.<LlmReviewTask>lambdaQuery()
                .eq(LlmReviewTask::getDetectLogId, detectLogId);
        LlmReviewTask existing = llmReviewTaskMapper.selectOne(wrapper);
        if (existing != null) {
            throw new BusinessException(ErrorCode.DATA_ALREADY_EXISTS, "该检测记录已存在复检任务");
        }

        // 检查检测记录是否存在
        CodeGuardDetectLogView detectLog = detectLogMapper.selectById(detectLogId);
        if (detectLog == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "检测记录不存在");
        }

        // 创建任务
        LlmReviewTask task = new LlmReviewTask();
        task.setDetectLogId(detectLogId);
        task.setTaskStatus("PENDING");
        task.setRetryCount(0);
        task.setMaxRetry(3);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        llmReviewTaskMapper.insert(task);
        log.info("创建复检任务成功，taskId={}, detectLogId={}", task.getId(), detectLogId);
        return task.getId();
    }

    /**
     * 批量创建复检任务。
     *
     * @param detectLogIds 检测记录 ID 列表
     * @return 包含成功/失败/跳过数量的统计
     */
    @Transactional
    public Map<String, Object> batchCreateReviewTask(List<Long> detectLogIds) {
        int success = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (Long detectLogId : detectLogIds) {
            try {
                LambdaQueryWrapper<LlmReviewTask> wrapper = Wrappers.<LlmReviewTask>lambdaQuery()
                        .eq(LlmReviewTask::getDetectLogId, detectLogId);
                LlmReviewTask existing = llmReviewTaskMapper.selectOne(wrapper);
                if (existing != null) {
                    skipped++;
                    continue;
                }
                CodeGuardDetectLogView detectLog = detectLogMapper.selectById(detectLogId);
                if (detectLog == null) {
                    failed++;
                    errors.add("检测记录不存在: " + detectLogId);
                    continue;
                }
                LlmReviewTask task = new LlmReviewTask();
                task.setDetectLogId(detectLogId);
                task.setTaskStatus("PENDING");
                task.setRetryCount(0);
                task.setMaxRetry(3);
                task.setCreatedAt(LocalDateTime.now());
                task.setUpdatedAt(LocalDateTime.now());
                llmReviewTaskMapper.insert(task);
                success++;
            } catch (Exception e) {
                failed++;
                errors.add("ID=" + detectLogId + ": " + e.getMessage());
            }
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("success", success);
        result.put("skipped", skipped);
        result.put("failed", failed);
        result.put("total", detectLogIds.size());
        result.put("errors", errors);
        log.info("批量创建复检任务完成: total={}, success={}, skipped={}, failed={}",
                detectLogIds.size(), success, skipped, failed);
        return result;
    }

    /**
     * 异步执行复检任务。
     *
     * @param taskId 任务 ID
     */
    @Async
    public void executeReviewTask(Long taskId) {
        LlmReviewTask task = llmReviewTaskMapper.selectById(taskId);
        if (task == null || !"PENDING".equals(task.getTaskStatus())) {
            log.warn("任务不存在或状态不是 PENDING，taskId={}", taskId);
            return;
        }

        // 更新状态为 RUNNING
        task.setTaskStatus("RUNNING");
        task.setStartTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        llmReviewTaskMapper.updateById(task);

        try {
            // 获取检测记录
            CodeGuardDetectLogView detectLog = detectLogMapper.selectById(task.getDetectLogId());
            if (detectLog == null) {
                throw new RuntimeException("检测记录不存在");
            }

            // 调用大模型
            LlmClientService.LlmReviewResult result = llmClientService.callLlmForReview(
                    detectLog.getCodeSnippet(),
                    detectLog.getLabel(),
                    detectLog.getRawOutput()
            );

            // 更新任务结果
            task.setLlmProvider(result.getProvider());
            task.setLlmModel(result.getModel());
            task.setLlmLabel(result.getLabel());
            task.setLlmExplanation(result.getExplanation());
            task.setLlmLatencyMs(result.getLatencyMs());
            task.setLlmResponse(result.getRawResponse());
            task.setTaskStatus("SUCCESS");
            task.setEndTime(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());

            llmReviewTaskMapper.updateById(task);
            log.info("复检任务执行成功，taskId={}, llmLabel={}", taskId, result.getLabel());

        } catch (Exception e) {
            log.error("复检任务执行失败，taskId={}", taskId, e);
            task.setRetryCount(task.getRetryCount() + 1);
            task.setLlmErrorMessage(e.getMessage());

            if (task.getRetryCount() >= task.getMaxRetry()) {
                task.setTaskStatus("FAILED");
                task.setEndTime(LocalDateTime.now());
                log.error("复检任务重试次数已达上限，标记为 FAILED，taskId={}", taskId);
            } else {
                task.setTaskStatus("PENDING");
                log.info("复检任务失败，等待重试，taskId={}, retryCount={}", taskId, task.getRetryCount());
            }

            task.setUpdatedAt(LocalDateTime.now());
            llmReviewTaskMapper.updateById(task);
        }
    }

    /**
     * 人工复核。
     *
     * @param taskId  任务 ID
     * @param request 复核请求
     */
    @Transactional
    public void humanReview(Long taskId, HumanReviewRequest request) {
        LlmReviewTask task = llmReviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "复检任务不存在");
        }

        if (!"SUCCESS".equals(task.getTaskStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只能对成功的复检任务进行人工复核");
        }

        // 验证参数
        if ("DISAGREED".equals(request.getHumanReviewStatus()) && request.getHumanLabel() == null) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不同意大模型判定时，必须提供人工判定标签");
        }

        // 更新复核结果
        task.setHumanReviewStatus(request.getHumanReviewStatus());
        task.setHumanLabel(request.getHumanLabel());
        task.setHumanRemark(request.getHumanRemark());
        task.setReviewerId(SecurityUtils.getUserId());
        task.setReviewTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());

        llmReviewTaskMapper.updateById(task);
        log.info("人工复核成功，taskId={}, status={}, label={}",
                taskId, request.getHumanReviewStatus(), request.getHumanLabel());
    }

    /**
     * 分页查询复检任务。
     */
    public Page<LlmReviewVO> pageReviewTasks(LlmReviewQuery query) {
        LambdaQueryWrapper<LlmReviewTask> wrapper = buildQueryWrapper(query);
        wrapper.orderByDesc(LlmReviewTask::getCreatedAt);

        Page<LlmReviewTask> page = new Page<>(query.getPageNum(), query.getPageSize());
        Page<LlmReviewTask> result = llmReviewTaskMapper.selectPage(page, wrapper);

        // 转换为 VO
        List<LlmReviewVO> voList = result.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 填充关联数据
        fillRelatedData(voList, result.getRecords());

        Page<LlmReviewVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 查询单个复检任务详情。
     */
    public LlmReviewVO getReviewTaskDetail(Long taskId) {
        LlmReviewTask task = llmReviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "复检任务不存在");
        }
        LlmReviewVO vo = convertToVO(task);
        fillRelatedData(List.of(vo), List.of(task));
        return vo;
    }

    /**
     * 取消复检任务。
     */
    @Transactional
    public void cancelReviewTask(Long taskId) {
        LlmReviewTask task = llmReviewTaskMapper.selectById(taskId);
        if (task == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "复检任务不存在");
        }

        if (!"PENDING".equals(task.getTaskStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "只能取消 PENDING 状态的任务");
        }

        task.setTaskStatus("CANCELLED");
        task.setEndTime(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        llmReviewTaskMapper.updateById(task);
        log.info("取消复检任务，taskId={}", taskId);
    }

    /**
     * 定时调度：批量执行待复检任务。
     *
     * <p>每 30 分钟执行一次，处理 PENDING 状态的任务。</p>
     */
    @Scheduled(cron = "${llm.review.cron:0 0/30 * * * ?}")
    public void scheduleReviewTasks() {
        if (!sysConfigReader.getBoolean(SysConfigReader.Keys.LLM_REVIEW_ENABLED)) {
            log.debug("大模型复检功能未开启，跳过调度");
            return;
        }

        int batchSize = sysConfigReader.getInt(SysConfigReader.Keys.LLM_REVIEW_BATCH_SIZE);
        LambdaQueryWrapper<LlmReviewTask> wrapper = Wrappers.<LlmReviewTask>lambdaQuery()
                .eq(LlmReviewTask::getTaskStatus, "PENDING")
                .orderByAsc(LlmReviewTask::getCreatedAt)
                .last("LIMIT " + batchSize);

        List<LlmReviewTask> pendingTasks = llmReviewTaskMapper.selectList(wrapper);
        log.info("定时调度：发现 {} 个待复检任务", pendingTasks.size());

        for (LlmReviewTask task : pendingTasks) {
            try {
                executeReviewTask(task.getId());
            } catch (Exception e) {
                log.error("调度执行复检任务失败，taskId={}", task.getId(), e);
            }
        }
    }

    // ===================== 内部方法 =====================

    private LambdaQueryWrapper<LlmReviewTask> buildQueryWrapper(LlmReviewQuery query) {
        LambdaQueryWrapper<LlmReviewTask> wrapper = Wrappers.<LlmReviewTask>lambdaQuery()
                .eq(query.getDetectLogId() != null, LlmReviewTask::getDetectLogId, query.getDetectLogId())
                .eq(query.getTaskStatus() != null && !query.getTaskStatus().isEmpty(),
                        LlmReviewTask::getTaskStatus, query.getTaskStatus())
                .eq(query.getLlmLabel() != null && !query.getLlmLabel().isEmpty(),
                        LlmReviewTask::getLlmLabel, query.getLlmLabel())
                .eq(query.getHumanReviewStatus() != null && !query.getHumanReviewStatus().isEmpty(),
                        LlmReviewTask::getHumanReviewStatus, query.getHumanReviewStatus())
                .eq(query.getHumanLabel() != null && !query.getHumanLabel().isEmpty(),
                        LlmReviewTask::getHumanLabel, query.getHumanLabel());

        if (query.getBeginTime() != null) {
            wrapper.ge(LlmReviewTask::getCreatedAt, query.getBeginTime());
        }
        if (query.getEndTime() != null) {
            wrapper.le(LlmReviewTask::getCreatedAt, query.getEndTime());
        }

        return wrapper;
    }

    private LlmReviewVO convertToVO(LlmReviewTask task) {
        LlmReviewVO vo = new LlmReviewVO();
        vo.setId(task.getId());
        vo.setDetectLogId(task.getDetectLogId());
        vo.setTaskStatus(task.getTaskStatus());
        vo.setRetryCount(task.getRetryCount());
        vo.setMaxRetry(task.getMaxRetry());
        vo.setLlmProvider(task.getLlmProvider());
        vo.setLlmModel(task.getLlmModel());
        vo.setLlmLabel(task.getLlmLabel());
        vo.setLlmExplanation(task.getLlmExplanation());
        vo.setLlmLatencyMs(task.getLlmLatencyMs());
        vo.setLlmErrorMessage(task.getLlmErrorMessage());
        vo.setHumanReviewStatus(task.getHumanReviewStatus());
        vo.setHumanLabel(task.getHumanLabel());
        vo.setHumanRemark(task.getHumanRemark());
        vo.setReviewerId(task.getReviewerId());
        vo.setReviewTime(task.getReviewTime());
        vo.setStartTime(task.getStartTime());
        vo.setEndTime(task.getEndTime());
        vo.setCreatedAt(task.getCreatedAt());
        vo.setUpdatedAt(task.getUpdatedAt());
        return vo;
    }

    private void fillRelatedData(List<LlmReviewVO> voList, List<LlmReviewTask> taskList) {
        if (taskList.isEmpty()) {
            return;
        }

        // 批量加载检测记录
        List<Long> detectLogIds = taskList.stream()
                .map(LlmReviewTask::getDetectLogId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, CodeGuardDetectLogView> detectLogMap = detectLogMapper.selectBatchIds(detectLogIds)
                .stream()
                .collect(Collectors.toMap(CodeGuardDetectLogView::getId, Function.identity()));

        // 批量加载复核人
        List<Long> reviewerIds = taskList.stream()
                .map(LlmReviewTask::getReviewerId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, AdminUser> reviewerMap = reviewerIds.isEmpty()
                ? Map.of()
                : adminUserMapper.selectBatchIds(reviewerIds)
                        .stream()
                        .collect(Collectors.toMap(AdminUser::getId, Function.identity()));

        // 填充 VO
        for (LlmReviewVO vo : voList) {
            CodeGuardDetectLogView detectLog = detectLogMap.get(vo.getDetectLogId());
            if (detectLog != null) {
                vo.setCodeSnippet(detectLog.getCodeSnippet());
                vo.setSmallModelLabel(detectLog.getLabel());
                vo.setSmallModelRawOutput(detectLog.getRawOutput());
            }

            if (vo.getReviewerId() != null) {
                AdminUser reviewer = reviewerMap.get(vo.getReviewerId());
                if (reviewer != null) {
                    vo.setReviewerName(reviewer.getNickname() != null ? reviewer.getNickname() : reviewer.getUsername());
                }
            }
        }
    }
}
