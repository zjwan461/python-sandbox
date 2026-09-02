package io.github.sandbox.admin.apikey.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.apikey.entity.ClientApiKey;

/** client_api_key Mapper（SELF 数据权限由 AdminDataPermissionHandler 对该表自动行过滤） */
public interface ClientApiKeyMapper extends BaseMapper<ClientApiKey> {
}
