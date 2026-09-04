package io.github.sandbox.admin.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.log.entity.LlmReviewTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 大模型复检任务 Mapper。
 */
@Mapper
public interface LlmReviewTaskMapper extends BaseMapper<LlmReviewTask> {
}
