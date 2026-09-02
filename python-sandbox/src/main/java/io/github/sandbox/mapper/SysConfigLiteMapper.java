package io.github.sandbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.entity.SysConfigLite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 系统设置KV只读Mapper（T-0024 匿名灰度与全局默认限流值拉取用）
 */
@Mapper
public interface SysConfigLiteMapper extends BaseMapper<SysConfigLite> {
}
