package io.github.sandbox.admin.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.DataPermissionInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import io.github.sandbox.admin.common.datapermission.AdminDataPermissionHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Plus 插件链配置（T-0009/T-0021）。
 *
 * <p>顺序：分页 → 数据权限 → 全表更新/删除防护。
 * 数据权限实现见 {@link AdminDataPermissionHandler}。</p>
 */
@Configuration
@RequiredArgsConstructor
public class MybatisPlusConfig {

    private final AdminDataPermissionHandler dataPermissionHandler;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 分页插件
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));

        // 数据权限插件（行级过滤，集中维护）
        DataPermissionInterceptor dp = new DataPermissionInterceptor();
        dp.setDataPermissionHandler(dataPermissionHandler);
        interceptor.addInnerInterceptor(dp);

        // 防全表更新与删除
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
