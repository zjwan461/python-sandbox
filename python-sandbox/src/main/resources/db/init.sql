-- 创建数据库
CREATE DATABASE IF NOT EXISTS sandbox CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE sandbox;

-- API请求日志表
CREATE TABLE IF NOT EXISTS api_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    trace_id VARCHAR(64) COMMENT '请求追踪ID',
    session_id VARCHAR(64) COMMENT '会话ID',
    api_path VARCHAR(255) COMMENT 'API路径',
    http_method VARCHAR(10) COMMENT 'HTTP方法',
    request_params TEXT COMMENT '请求参数（JSON格式）',
    response_code INT COMMENT '响应状态码',
    execution_time BIGINT COMMENT '执行耗时（毫秒）',
    client_ip VARCHAR(64) COMMENT '客户端IP',
    client_id BIGINT NULL COMMENT '归属客户端（client_app.id；无鉴权上下文为NULL）',
    api_key_id BIGINT NULL COMMENT '调用ApiKey（client_api_key.id；匿名调用为NULL）',
    owner_user_id BIGINT NULL COMMENT '归属用户（admin_user.id，数据权限 SELF 过滤键）',
    rate_limit_hit TINYINT NOT NULL DEFAULT 0 COMMENT '限流命中标志：0=未命中 1=命中（默认决策#2）',
    rate_limit_rule_id BIGINT NULL COMMENT '命中的限流规则主键（ratelimit_rule.id；未命中为NULL）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_trace_id (trace_id),
    INDEX idx_session_id (session_id),
    INDEX idx_api_path (api_path),
    INDEX idx_created_at (created_at),
    INDEX idx_api_log_client (client_id),
    INDEX idx_api_log_api_key (api_key_id),
    INDEX idx_api_log_owner_user (owner_user_id),
    INDEX idx_api_log_rl_hit (rate_limit_hit)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='API请求日志表';

-- 沙箱操作日志表
CREATE TABLE IF NOT EXISTS sandbox_operation_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    trace_id VARCHAR(64) COMMENT '请求追踪ID',
    session_id VARCHAR(64) COMMENT '会话ID',
    operation_type VARCHAR(32) COMMENT '操作类型：PYTHON_EXEC, SHELL_EXEC, PIP_INSTALL, PIP_UNINSTALL等',
    operation_content LONGTEXT COMMENT '操作内容（Python代码、Shell命令、包名等）',
    result VARCHAR(16) COMMENT '执行结果：SUCCESS, FAILED',
    exit_code INT COMMENT '退出码',
    stdout LONGTEXT COMMENT '标准输出',
    stderr LONGTEXT COMMENT '标准错误',
    execution_time BIGINT COMMENT '执行耗时（毫秒）',
    error_message TEXT COMMENT '错误信息',
    client_id BIGINT NULL COMMENT '归属客户端（client_app.id；无鉴权上下文为NULL）',
    api_key_id BIGINT NULL COMMENT '归属ApiKey（client_api_key.id）',
    owner_user_id BIGINT NULL COMMENT '归属用户（admin_user.id，数据权限 SELF 过滤键）',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_trace_id (trace_id),
    INDEX idx_session_id (session_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_result (result),
    INDEX idx_created_at (created_at),
    INDEX idx_sol_client (client_id),
    INDEX idx_sol_api_key (api_key_id),
    INDEX idx_sol_owner_user (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='沙箱操作日志表';
