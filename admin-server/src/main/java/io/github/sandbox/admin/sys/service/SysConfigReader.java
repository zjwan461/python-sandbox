package io.github.sandbox.admin.sys.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.sys.entity.SysConfig;
import io.github.sandbox.admin.sys.mapper.SysConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 系统设置基础读取组件（T-0012，design.md §4.5、§7.6、§11.1）。
 *
 * <p>特性：</p>
 * <ul>
 *   <li><b>拒绝未登记键</b>：仅允许读取 sys_config 表中已登记的 config_key，未知键抛
 *       {@link ErrorCode#CONFIG_KEY_UNKNOWN}。</li>
 *   <li><b>按 value_type 强校验</b>：NUMBER/BOOLEAN 读取时做类型解析，非法值回落默认并告警。</li>
 *   <li><b>本地缓存</b>：全量懒加载 + TTL（60s）过期刷新；更新值后调用 {@link #refresh()} 立即失效。</li>
 *   <li><b>缺失或非法配置具有明确默认业务值</b>：见 {@link Keys} 常量关联的默认值表。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SysConfigReader {

    /** 受控配置键（与 seed/001-admin-seed.sql §6 预置键一一对应） */
    public static final class Keys {
        public static final String REGISTER_ALLOWED = "register.allowed";
        public static final String LOGIN_FAIL_THRESHOLD = "login.fail.threshold";
        public static final String LOGIN_LOCK_MINUTES = "login.lock.minutes";
        public static final String REMEMBER_ME_MAX_DAYS = "remember.me.max.days";
        public static final String RATELIMIT_DEFAULT_MINUTE = "ratelimit.default.minute";
        public static final String RATELIMIT_DEFAULT_HOUR = "ratelimit.default.hour";
        public static final String RATELIMIT_DEFAULT_DAY = "ratelimit.default.day";
        public static final String RATELIMIT_ANONYMOUS_ALLOWED = "ratelimit.anonymous.allowed";

        private Keys() {
        }
    }

    /** 键缺失/非法时的明确默认业务值（requirements.md 默认口径） */
    private static final Map<String, String> DEFAULTS = Map.of(
            Keys.REGISTER_ALLOWED, "false",
            Keys.LOGIN_FAIL_THRESHOLD, "5",
            Keys.LOGIN_LOCK_MINUTES, "30",
            Keys.REMEMBER_ME_MAX_DAYS, "14",
            Keys.RATELIMIT_DEFAULT_MINUTE, "60",
            Keys.RATELIMIT_DEFAULT_HOUR, "1000",
            Keys.RATELIMIT_DEFAULT_DAY, "10000",
            Keys.RATELIMIT_ANONYMOUS_ALLOWED, "false"
    );

    /** 本地缓存 TTL（毫秒） */
    private static final long CACHE_TTL_MS = 60_000L;

    private final SysConfigMapper sysConfigMapper;

    /** 缓存：configKey -> raw value（含登记类型信息按行缓存） */
    private volatile Map<String, SysConfig> cache = new ConcurrentHashMap<>();
    private final AtomicLong cacheLoadedAt = new AtomicLong(0L);

    /** 读取字符串值（按登记类型强校验）；未登记键抛 CONFIG_KEY_UNKNOWN，值非法抛 CONFIG_VALUE_INVALID */
    public String getString(String key) {
        SysConfig config = requireRegistered(key);
        return validateAndGet(config);
    }

    /**
     * 读取整型值：未登记键拒绝；值缺失/非法具有明确默认业务值（T-0012 验收）。
     */
    public int getInt(String key) {
        String raw = getRawOrEmpty(key);
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("sys_config [{}] 值非法（期望 NUMBER）：{}，回落默认", key, raw);
            return Integer.parseInt(DEFAULTS.getOrDefault(key, "0"));
        }
    }

    /** 读取布尔值：未登记键拒绝；非法值回落默认 */
    public boolean getBoolean(String key) {
        String raw = getRawOrEmpty(key);
        if ("true".equalsIgnoreCase(raw.trim())) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw.trim())) {
            return false;
        }
        log.warn("sys_config [{}] 值非法（期望 BOOLEAN）：{}，回落默认", key, raw);
        return Boolean.parseBoolean(DEFAULTS.getOrDefault(key, "false"));
    }

    /** 登录失败锁定阈值（连续失败达到该次数即锁定） */
    public int loginFailThreshold() {
        return getInt(Keys.LOGIN_FAIL_THRESHOLD);
    }

    /** 登录锁定时长（分钟） */
    public int loginLockMinutes() {
        return getInt(Keys.LOGIN_LOCK_MINUTES);
    }

    /** 最长免登天数（Remember-Me 使用，T-0034） */
    public int rememberMeMaxDays() {
        return getInt(Keys.REMEMBER_ME_MAX_DAYS);
    }

    /** 是否允许新注册 */
    public boolean registerAllowed() {
        return getBoolean(Keys.REGISTER_ALLOWED);
    }

    /** 使本地缓存立即失效（系统设置更新后调用） */
    public void refresh() {
        cacheLoadedAt.set(0L);
        cache = new ConcurrentHashMap<>();
    }

    // ===================== internal =====================

    /** 已登记键的原始值（不做类型断言，交由 getInt/getBoolean 的默认回落逻辑处理） */
    private String getRawOrEmpty(String key) {
        SysConfig config = requireRegistered(key);
        return config.getConfigValue() == null ? "" : config.getConfigValue();
    }

    private SysConfig requireRegistered(String key) {
        ensureCache();
        SysConfig config = cache.get(key);
        if (config == null) {
            // 未登记键：业务接口拒绝
            throw new BusinessException(ErrorCode.CONFIG_KEY_UNKNOWN, "未识别的设置键: " + key);
        }
        return config;
    }

    private String validateAndGet(SysConfig config) {
        String value = config.getConfigValue();
        String type = config.getValueType() == null ? "STRING" : config.getValueType().toUpperCase();
        switch (type) {
            case "NUMBER" -> {
                try {
                    Long.parseLong(value.trim());
                } catch (NumberFormatException e) {
                    throw new BusinessException(ErrorCode.CONFIG_VALUE_INVALID,
                            "设置键 " + config.getConfigKey() + " 期望 NUMBER，实际值非法");
                }
            }
            case "BOOLEAN" -> {
                if (!"true".equalsIgnoreCase(value.trim()) && !"false".equalsIgnoreCase(value.trim())) {
                    throw new BusinessException(ErrorCode.CONFIG_VALUE_INVALID,
                            "设置键 " + config.getConfigKey() + " 期望 BOOLEAN，实际值非法");
                }
            }
            case "JSON" -> {
                if (value == null || (!value.trim().startsWith("{") && !value.trim().startsWith("["))) {
                    throw new BusinessException(ErrorCode.CONFIG_VALUE_INVALID,
                            "设置键 " + config.getConfigKey() + " 期望 JSON，实际值非法");
                }
            }
            default -> {
                // STRING：不做额外校验
            }
        }
        return value;
    }

    private void ensureCache() {
        long loaded = cacheLoadedAt.get();
        if (System.currentTimeMillis() - loaded < CACHE_TTL_MS && !cache.isEmpty()) {
            return;
        }
        synchronized (this) {
            if (System.currentTimeMillis() - cacheLoadedAt.get() < CACHE_TTL_MS && !cache.isEmpty()) {
                return;
            }
            try {
                Map<String, SysConfig> fresh = new ConcurrentHashMap<>();
                sysConfigMapper.selectList(Wrappers.<SysConfig>lambdaQuery())
                        .forEach(c -> fresh.put(c.getConfigKey(), c));
                cache = fresh;
            } catch (Exception e) {
                // DB 异常时保留旧缓存（保守方向），仅时间戳前移避免风暴
                log.error("加载 sys_config 失败，保留旧缓存：{}", e.getMessage());
            }
            cacheLoadedAt.set(System.currentTimeMillis());
        }
    }
}
