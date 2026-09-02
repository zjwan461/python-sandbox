package io.github.sandbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.entity.ClientApp;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户端应用只读Mapper（T-0023 认证校验用）
 */
@Mapper
public interface ClientAppMapper extends BaseMapper<ClientApp> {
}
