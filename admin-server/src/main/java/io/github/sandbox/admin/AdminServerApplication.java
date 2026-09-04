package io.github.sandbox.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * admin-server 启动类（T-0010）。
 *
 * <p>独立管理端后端工程：API 前缀 {@code /admin-api/**}（server.servlet.context-path），
 * 端口 9090，与 {@code python-sandbox}（8080，{@code /api/sandbox/**}）完全分离。
 * 本工程不得 import、复制或直接调用 {@code python-sandbox} 下的任何类、常量或工具；
 * 两工程仅通过 {@code /internal/**} HTTP 接口或共享 MySQL 库交互（见 cross-cutting/README.md）。</p>
 */
@SpringBootApplication
@MapperScan("io.github.sandbox.admin.**.mapper")
@EnableAsync
@EnableScheduling
public class AdminServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServerApplication.class, args);
    }
}
