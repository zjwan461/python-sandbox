package io.github.sandbox.admin.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.audit.entity.AdminLoginLog;

/** admin_login_log Mapper（只追加；查询页属批次3/4 的 T-0020，本批次仅落库） */
public interface AdminLoginLogMapper extends BaseMapper<AdminLoginLog> {
}
