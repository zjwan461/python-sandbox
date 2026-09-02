package io.github.sandbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.entity.RatelimitRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 限流规则只读Mapper（T-0024 拉取用）
 */
@Mapper
public interface RatelimitRuleMapper extends BaseMapper<RatelimitRule> {
}
