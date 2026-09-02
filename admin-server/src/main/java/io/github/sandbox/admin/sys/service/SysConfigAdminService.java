package io.github.sandbox.admin.sys.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.sys.entity.SysConfig;
import io.github.sandbox.admin.sys.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 系统设置管理业务（T-0041，FR-SYS-01；design.md §4.5、§7.6、§10.3 /sys/configs）。
 *
 * <p>受控口径：</p>
 * <ul>
 *   <li>仅允许更新 sys_config 中已登记的稳定键（未识别键拒绝，11008）。</li>
 *   <li>值按登记 value_type 强校验（11009）；不允许新增/删除键，不开放任意 KV。</li>
 *   <li>敏感业务凭证（内部共享密钥、ApiKey 明文等）不在本表登记，天然无法经此配置（验收）。</li>
 *   <li>更新成功后使 {@link SysConfigReader} 本地缓存立即失效，登录/限流/认证链路即时读到新值。</li>
 * </ul>
 *
 * <p>权限口径：Controller 层 sysconfig:view / sysconfig:edit（种子授权=超管/管理员，
 * 审计员只读，普通用户无入口且后端独立拒绝）。</p>
 */
@Service
@RequiredArgsConstructor
public class SysConfigAdminService {

    private final SysConfigMapper sysConfigMapper;
    private final SysConfigReader sysConfigReader;

    /** 受控列表（全部已登记键，按 id 升序） */
    public List<SysConfig> list() {
        return sysConfigMapper.selectList(Wrappers.<SysConfig>lambdaQuery()
                .orderByAsc(SysConfig::getId));
    }

    /**
     * 批量更新（仅值；键/类型/名称不可改）。
     *
     * @param updates configKey -> newValue
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdate(Map<String, String> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "没有需要更新的设置项");
        }
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            SysConfig existing = sysConfigMapper.selectOne(Wrappers.<SysConfig>lambdaQuery()
                    .eq(SysConfig::getConfigKey, entry.getKey()).last("LIMIT 1"));
            if (existing == null) {
                throw new BusinessException(ErrorCode.CONFIG_KEY_UNKNOWN, "未识别的设置键: " + entry.getKey());
            }
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            validateValueType(existing.getValueType(), value, entry.getKey());
            SysConfig update = new SysConfig();
            update.setId(existing.getId());
            update.setConfigValue(value);
            sysConfigMapper.updateById(update);
        }
        sysConfigReader.refresh(); // 立即失效本地缓存，业务链路即时生效
    }

    private void validateValueType(String valueType, String value, String key) {
        String type = valueType == null ? "STRING" : valueType.toUpperCase();
        switch (type) {
            case "NUMBER" -> {
                try {
                    Long.parseLong(value);
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.CONFIG_VALUE_INVALID,
                            "设置键 " + key + " 期望 NUMBER，实际值非法: " + value);
                }
            }
            case "BOOLEAN" -> {
                if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                    throw new BusinessException(ErrorCode.CONFIG_VALUE_INVALID,
                            "设置键 " + key + " 期望 BOOLEAN，实际值非法: " + value);
                }
            }
            case "JSON" -> {
                if (value.isEmpty() || (!value.startsWith("{") && !value.startsWith("["))) {
                    throw new BusinessException(ErrorCode.CONFIG_VALUE_INVALID,
                            "设置键 " + key + " 期望 JSON，实际值非法");
                }
            }
            default -> {
                // STRING 不额外校验；但不得为空值破坏 Not NULL 列
                if (value.isEmpty()) {
                    throw new BusinessException(ErrorCode.CONFIG_VALUE_INVALID,
                            "设置键 " + key + " 的值不能为空");
                }
            }
        }
    }
}
