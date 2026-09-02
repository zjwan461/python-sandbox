package io.github.sandbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.entity.AdminUserLite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理端用户状态只读Mapper（T-0023 USER_DISABLED 校验用）
 */
@Mapper
public interface AdminUserLiteMapper extends BaseMapper<AdminUserLite> {
}
