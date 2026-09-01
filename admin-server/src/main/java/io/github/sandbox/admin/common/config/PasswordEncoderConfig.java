package io.github.sandbox.admin.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器（T-0015）：仅使用 spring-security-crypto 的 BCryptPasswordEncoder，
 * 不引入 spring-boot-starter-security（任务硬约束）。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // 默认 BCrypt cost=10，与种子数据 $2b$10$... 哈希兼容
        return new BCryptPasswordEncoder();
    }
}
