package io.github.sandbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.entity.ApiLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * API日志Mapper接口
 */
@Mapper
public interface ApiLogMapper extends BaseMapper<ApiLog> {
}
