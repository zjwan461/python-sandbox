package io.github.sandbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.entity.CodeGuardDetectLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * CodeGuard 模型推理检测记录写入 Mapper（python-sandbox 侧仅写入，不做查询展示）。
 */
@Mapper
public interface CodeGuardDetectLogMapper extends BaseMapper<CodeGuardDetectLog> {
}
