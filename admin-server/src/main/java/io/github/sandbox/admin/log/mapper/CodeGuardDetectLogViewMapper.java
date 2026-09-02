package io.github.sandbox.admin.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.log.entity.CodeGuardDetectLogView;
import org.apache.ibatis.annotations.Mapper;

/**
 * CodeGuard 检测记录只读 Mapper（admin-server 侧仅查询展示，不写入）。
 */
@Mapper
public interface CodeGuardDetectLogViewMapper extends BaseMapper<CodeGuardDetectLogView> {
}
