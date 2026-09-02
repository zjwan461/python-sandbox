-- =====================================================================
-- 003-codeguard.sql — 代码危险检测策略（CodeGuard）sys_config 种子键
-- 说明：
--   * 注册 python-sandbox 执行前校验的两个独立策略开关与降级策略，
--     管理端"系统设置"页面（/sys/configs）可即时修改；
--   * python-sandbox 侧 CodeGuardService 定时拉取（默认 60s）生效；
--   * 模型推理服务地址不入库（基础设施配置，经 ENV
--     SANDBOX_CODEGUARD_DETECT_BASE_URL 注入，见 design 口径：敏感/基础设施
--     配置不进 sys_config）。
-- =====================================================================

USE sandbox;

INSERT IGNORE INTO sys_config
    (id, config_key, config_value, value_type, config_name, description, is_built_in, create_by, update_by)
VALUES
    (9,  'codeguard.static.enabled',  'true',  'BOOLEAN', '静态代码校验策略',   '执行 Python 前启用正则黑名单静态扫描（模块/函数/调用三层校验）', 1, 'system', 'system'),
    (10, 'codeguard.model.enabled',   'false', 'BOOLEAN', '模型推理检测策略',   '执行 Python 前调用微调模型推理服务（train/infer）判定代码危险性；需推理服务在线', 1, 'system', 'system'),
    (11, 'codeguard.model.fail-open', 'true',  'BOOLEAN', '推理失败放行开关',   'true=推理服务不可用时放行（fail-open，可用性优先）；false=拒绝执行（fail-close，安全优先）', 1, 'system', 'system');
