package io.github.sandbox.admin.ratelimit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.ratelimit.entity.RatelimitRule;

/** ratelimit_rule Mapper（元数据表，不受 SELF 数据权限行过滤；归属校验在 Service 层） */
public interface RatelimitRuleMapper extends BaseMapper<RatelimitRule> {
}
