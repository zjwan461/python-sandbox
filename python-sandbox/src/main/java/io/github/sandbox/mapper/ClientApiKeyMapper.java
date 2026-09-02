package io.github.sandbox.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.entity.ClientApiKey;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户端ApiKey只读Mapper（T-0023 认证校验用，按 key_hash 查表）
 */
@Mapper
public interface ClientApiKeyMapper extends BaseMapper<ClientApiKey> {
}
