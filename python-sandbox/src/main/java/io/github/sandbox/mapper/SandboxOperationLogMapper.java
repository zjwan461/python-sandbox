package io.github.sandbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.entity.SandboxOperationLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 沙箱操作日志Mapper接口
 */
@Mapper
public interface SandboxOperationLogMapper extends BaseMapper<SandboxOperationLog> {
}
