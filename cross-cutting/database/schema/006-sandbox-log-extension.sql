-- =====================================================================
-- 006-sandbox-log-extension.sql — 扩展既有 api_log / sandbox_operation_log（T-0004/T-0008 关联，配合 T-0022）
-- 依据：design.md §7.5（默认决策 #2：限流命中复用 api_log，不建 ratelimit_hit_log）、§8.5、§8.6
-- 硬约束：
--   * 不破坏既有列与既有写入语义：仅追加新列，全部可空或有默认值。
--   * rate_limit_hit / rate_limit_rule_id 扩展在现有 api_log 表上（任务硬性要求）。
--   * 无鉴权上下文时新字段允许为 NULL。
--   * 脚本幂等：通过 information_schema 判断列是否存在，重复执行安全。
-- 归属键口径（与 design.md §5.1 一致）：
--   client_id       -> client_app.id
--   api_key_id      -> client_api_key.id
--   owner_user_id   -> admin_user.id（由 ApiKey 绑定用户或客户端归属用户解析）
--   rate_limit_rule_id -> ratelimit_rule.id
-- =====================================================================

USE sandbox;

-- ---------------------------------------------------------------------
-- 幂等列添加过程（MySQL 不支持 ADD COLUMN IF NOT EXISTS）
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS admin_add_column;
DELIMITER $$
CREATE PROCEDURE admin_add_column(
    IN p_table  VARCHAR(64),
    IN p_column VARCHAR(64),
    IN p_ddl    TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = 'sandbox'
          AND TABLE_NAME   = p_table
          AND COLUMN_NAME  = p_column
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN ', p_ddl);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

-- ---------------------------------------------------------------------
-- api_log 扩展列（design.md §7.5、§8.6）
-- ---------------------------------------------------------------------
CALL admin_add_column('api_log', 'client_id',          "client_id BIGINT NULL COMMENT '归属客户端（client_app.id；无鉴权上下文为NULL）'");
CALL admin_add_column('api_log', 'api_key_id',         "api_key_id BIGINT NULL COMMENT '调用ApiKey（client_api_key.id；匿名调用为NULL）'");
CALL admin_add_column('api_log', 'owner_user_id',      "owner_user_id BIGINT NULL COMMENT '归属用户（admin_user.id，数据权限 SELF 过滤键）'");
CALL admin_add_column('api_log', 'rate_limit_hit',     "rate_limit_hit TINYINT NOT NULL DEFAULT 0 COMMENT '限流命中标志：0=未命中 1=命中（默认决策#2，不另建命中表）'");
CALL admin_add_column('api_log', 'rate_limit_rule_id', "rate_limit_rule_id BIGINT NULL COMMENT '命中的限流规则主键（ratelimit_rule.id；未命中为NULL）'");

-- 幂等索引（information_schema.STATISTICS 判断）
DROP PROCEDURE IF EXISTS admin_add_index;
DELIMITER $$
CREATE PROCEDURE admin_add_index(
    IN p_table  VARCHAR(64),
    IN p_index  VARCHAR(64),
    IN p_ddl    TEXT
)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = 'sandbox'
          AND TABLE_NAME   = p_table
          AND INDEX_NAME   = p_index
    ) THEN
        SET @ddl = CONCAT('ALTER TABLE `', p_table, '` ADD INDEX ', p_ddl);
        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END IF;
END$$
DELIMITER ;

CALL admin_add_index('api_log', 'idx_api_log_client',      "`idx_api_log_client` (client_id)");
CALL admin_add_index('api_log', 'idx_api_log_api_key',     "`idx_api_log_api_key` (api_key_id)");
CALL admin_add_index('api_log', 'idx_api_log_owner_user',  "`idx_api_log_owner_user` (owner_user_id)");
CALL admin_add_index('api_log', 'idx_api_log_rl_hit',      "`idx_api_log_rl_hit` (rate_limit_hit)");

-- ---------------------------------------------------------------------
-- sandbox_operation_log 扩展列（design.md §8.6）
-- ---------------------------------------------------------------------
CALL admin_add_column('sandbox_operation_log', 'client_id',     "client_id BIGINT NULL COMMENT '归属客户端（client_app.id；无鉴权上下文为NULL）'");
CALL admin_add_column('sandbox_operation_log', 'api_key_id',    "api_key_id BIGINT NULL COMMENT '归属ApiKey（client_api_key.id）'");
CALL admin_add_column('sandbox_operation_log', 'owner_user_id', "owner_user_id BIGINT NULL COMMENT '归属用户（admin_user.id，数据权限 SELF 过滤键）'");

CALL admin_add_index('sandbox_operation_log', 'idx_sol_client',     "`idx_sol_client` (client_id)");
CALL admin_add_index('sandbox_operation_log', 'idx_sol_api_key',    "`idx_sol_api_key` (api_key_id)");
CALL admin_add_index('sandbox_operation_log', 'idx_sol_owner_user', "`idx_sol_owner_user` (owner_user_id)");

DROP PROCEDURE IF EXISTS admin_add_column;
DROP PROCEDURE IF EXISTS admin_add_index;
