package io.github.sandbox.admin.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.log.entity.SandboxOperationLogView;

/** sandbox_operation_log 只读 Mapper（SELF 数据权限按 owner_user_id 行过滤） */
public interface SandboxOperationLogViewMapper extends BaseMapper<SandboxOperationLogView> {
}
