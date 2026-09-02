package io.github.sandbox.admin.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.log.entity.ApiLogView;

/** api_log 只读 Mapper（SELF 数据权限按 owner_user_id 行过滤，T-0021 注册表已含 api_log） */
public interface ApiLogViewMapper extends BaseMapper<ApiLogView> {
}
