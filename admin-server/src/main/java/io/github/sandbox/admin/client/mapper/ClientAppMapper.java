package io.github.sandbox.admin.client.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.sandbox.admin.client.entity.ClientApp;

/** client_app Mapper（SELF 数据权限由 AdminDataPermissionHandler 对该表自动行过滤） */
public interface ClientAppMapper extends BaseMapper<ClientApp> {
}
