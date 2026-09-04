package io.github.sandbox.admin.log.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大模型复检任务分页查询条件。
 */
@Data
public class LlmReviewQuery {

    /** 时间范围-起（按 created_at） */
    private LocalDateTime beginTime;

    /** 时间范围-止 */
    private LocalDateTime endTime;

    /** 任务状态：PENDING / RUNNING / SUCCESS / FAILED / CANCELLED */
    private String taskStatus;

    /** 大模型判定标签：SAFE / DANGEROUS */
    private String llmLabel;

    /** 人工复核状态：AGREED / DISAGREED（null 表示未复核） */
    private String humanReviewStatus;

    /** 人工最终判定标签：SAFE / DANGEROUS */
    private String humanLabel;

    /** 关联的检测记录 ID */
    private Long detectLogId;

    /** 排序字段（默认 createdAt 倒序） */
    private String orderBy;

    /** 是否升序（默认 false=倒序） */
    private Boolean asc = false;

    /** 页码（1起） */
    private long pageNum = 1;

    /** 每页条数 */
    private long pageSize = 20;
}
