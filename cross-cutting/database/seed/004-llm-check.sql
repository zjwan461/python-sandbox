-- =====================================================================
-- 004-llm-check.sql — 大模型复检（LLM Review）sys_config 种子键
-- 说明：
--   * 注册大模型复检功能的开关与连接配置；
--   * 默认不开启大模型复检（llm.review.enabled=false）；
--   * 管理端"系统设置"页面可即时修改，admin-server 60s 定时拉取生效；
--   * API Key 等敏感信息通过 ENV 注入（LLM_REVIEW_API_KEY），不入库。
-- =====================================================================

USE sandbox;

INSERT IGNORE INTO sys_config
    (id, config_key, config_value, value_type, config_name, description, is_built_in, create_by, update_by)
VALUES
    (20, 'llm.review.enabled',          'false', 'BOOLEAN', '大模型复检开关',       '是否开启大模型对小模型推理结果的异步复检；默认关闭', 1, 'system', 'system'),
    (21, 'llm.review.provider',         'openai',  'STRING',  '大模型提供商',         'OpenAI 兼容接口提供商标识（openai/deepseek/qwen 等）', 1, 'system', 'system'),
    (22, 'llm.review.api.endpoint',     '',        'STRING',  '大模型 API 地址',      'OpenAI 兼容的 API Base URL（如 https://api.deepseek.com/v1）', 1, 'system', 'system'),
    (23, 'llm.review.model.name',       'gpt-4o-mini', 'STRING', '大模型名称',       '调用大模型的 model 标识（如 deepseek-chat / qwen-turbo / gpt-4o-mini）', 1, 'system', 'system'),
    (24, 'llm.review.batch.size',       '50',      'NUMBER',  '单次复检批量大小',     '每次调度最多处理的待复检记录数', 1, 'system', 'system'),
    (25, 'llm.review.cron',             '0 0/30 * * * ?', 'STRING', '复检调度 Cron', '异步复检任务的调度周期（默认每 30 分钟一次）', 1, 'system', 'system');
