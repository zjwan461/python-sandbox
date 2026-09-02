package io.github.sandbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.sandbox.config.SandboxConfig;
import io.github.sandbox.context.AuthContext;
import io.github.sandbox.entity.RatelimitRule;
import io.github.sandbox.entity.SysConfigLite;
import io.github.sandbox.mapper.RatelimitRuleMapper;
import io.github.sandbox.mapper.SysConfigLiteMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 限流规则拉取与本地判定服务（T-0024，design.md §7.2~§7.5）。
 *
 * <p>规则来源：MySQL {@code ratelimit_rule} 表，拉取条件
 * {@code status=1 AND effective_time<=NOW() AND (expire_time IS NULL OR expire_time>NOW()) AND deleted=0}
 * （deleted 由 MyBatis-Plus 逻辑删除自动附加）。启动加载 + 定时刷新（默认决策 #3），
 * 管理端保存规则后经内部接口 {@code POST /internal/sandbox/ratelimit/reload} 触发立即重拉，
 * reload 失败由定时拉取兜底。</p>
 *
 * <p>判定顺序：白名单（rate_limit_exempt=1 跳过全部）→ API_KEY 维度滑动窗口 →
 * CLIENT 维度令牌桶 → GLOBAL 全局默认（sys_config 默认值 + dimension=GLOBAL 规则）。
 * 多规则叠加任一命中即拒绝（FR-RATELIMIT-02）。</p>
 *
 * <p>缓存失效策略：拉取异常时保留旧缓存（fail-open，宁少限不少限），并置
 * {@code staleCache=true}，供调用方获得"配置未最新"的明确业务语义。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatelimitService {

    public static final String DIMENSION_API_KEY = "API_KEY";
    public static final String DIMENSION_CLIENT = "CLIENT";
    public static final String DIMENSION_GLOBAL = "GLOBAL";

    /** sys_config 受控键：匿名调用灰度开关（默认决策 #10，默认 false） */
    public static final String CFG_ANONYMOUS_ALLOWED = "ratelimit.anonymous.allowed";
    /** sys_config 受控键：全局默认限流（FR-RATELIMIT-06） */
    public static final String CFG_DEFAULT_MINUTE = "ratelimit.default.minute";
    public static final String CFG_DEFAULT_HOUR = "ratelimit.default.hour";
    public static final String CFG_DEFAULT_DAY = "ratelimit.default.day";

    private final RatelimitRuleMapper ratelimitRuleMapper;
    private final SysConfigLiteMapper sysConfigLiteMapper;
    private final SandboxConfig sandboxConfig;

    /** API_KEY 维度规则缓存：apiKeyId -> rules（按 priority 升序） */
    private final AtomicReference<Map<Long, List<RatelimitRule>>> apiKeyRules = new AtomicReference<>(Map.of());
    /** CLIENT 维度规则缓存：clientId -> rules */
    private final AtomicReference<Map<Long, List<RatelimitRule>>> clientRules = new AtomicReference<>(Map.of());
    /** GLOBAL 维度规则缓存 */
    private final AtomicReference<List<RatelimitRule>> globalRules = new AtomicReference<>(List.of());
    /** sys_config 快照（匿名灰度 + 全局默认阈值） */
    private final AtomicReference<Map<String, String>> sysConfigCache = new AtomicReference<>(Map.of());
    /** 上次刷新成功时间；拉取失败时保留旧缓存并置 stale 标志 */
    @Getter
    private volatile boolean staleCache = false;
    @Getter
    private volatile LocalDateTime lastRefreshTime;

    /** ApiKey 滑动窗口记录：key = apiKeyId:windowType */
    private final Map<String, Deque<Long>> slidingWindows = new ConcurrentHashMap<>();
    /** 客户端令牌桶：key = clientId:windowType */
    private final Map<String, TokenBucket> tokenBuckets = new ConcurrentHashMap<>();
    /** 匿名调用全局计数（apiKeyId 为 null 时按 "anon" 键共享） */
    private static final String ANON_KEY = "anon";

    @PostConstruct
    public void init() {
        // 启动加载：同步拉取一次；失败不阻断启动（fail-open，保留空缓存由定时任务兜底）
        safeRefresh();
    }

    /** 定时刷新（默认 60s，可配置 sandbox.ratelimit.refresh-interval-millis） */
    @Scheduled(fixedRateString = "${sandbox.ratelimit.refresh-interval-millis:60000}")
    public void scheduledRefresh() {
        safeRefresh();
    }

    /**
     * 立即重拉规则（内部 reload 接口触发）。
     *
     * @return true=刷新成功；false=拉取失败（保留旧缓存，由下次定时拉取兜底）
     */
    public boolean reload() {
        return safeRefresh();
    }

    private synchronized boolean safeRefresh() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<RatelimitRule> all = ratelimitRuleMapper.selectList(new LambdaQueryWrapper<RatelimitRule>()
                    .eq(RatelimitRule::getStatus, 1)
                    .and(w -> w.isNull(RatelimitRule::getEffectiveTime).or().le(RatelimitRule::getEffectiveTime, now))
                    .and(w -> w.isNull(RatelimitRule::getExpireTime).or().gt(RatelimitRule::getExpireTime, now)));

            Map<Long, List<RatelimitRule>> byApiKey = new ConcurrentHashMap<>();
            Map<Long, List<RatelimitRule>> byClient = new ConcurrentHashMap<>();
            List<RatelimitRule> globals = new ArrayList<>();
            for (RatelimitRule rule : all) {
                if (rule.getPriority() == null) {
                    rule.setPriority(100);
                }
                switch (rule.getDimension() == null ? "" : rule.getDimension().toUpperCase()) {
                    case DIMENSION_API_KEY -> byApiKey.computeIfAbsent(rule.getTargetId(), k -> new ArrayList<>()).add(rule);
                    case DIMENSION_CLIENT -> byClient.computeIfAbsent(rule.getTargetId(), k -> new ArrayList<>()).add(rule);
                    case DIMENSION_GLOBAL -> globals.add(rule);
                    default -> log.warn("忽略未知限流维度 ruleId={} dimension={}", rule.getId(), rule.getDimension());
                }
            }
            byApiKey.values().forEach(l -> l.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority())));
            byClient.values().forEach(l -> l.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority())));
            globals.sort((a, b) -> Integer.compare(a.getPriority(), b.getPriority()));

            apiKeyRules.set(byApiKey);
            clientRules.set(byClient);
            globalRules.set(globals);
            sysConfigCache.set(loadSysConfig());

            staleCache = false;
            lastRefreshTime = now;
            log.info("限流规则刷新成功: apiKeyTargets={} clientTargets={} globalRules={}",
                    byApiKey.size(), byClient.size(), globals.size());
            return true;
        } catch (Exception e) {
            // 拉取失败：保留旧缓存（fail-open），置 stale 标志
            staleCache = true;
            log.error("限流规则拉取失败，保留旧缓存（stale），将由定时拉取兜底: {}", e.getMessage(), e);
            return false;
        }
    }

    private Map<String, String> loadSysConfig() {
        List<SysConfigLite> configs = sysConfigLiteMapper.selectList(new LambdaQueryWrapper<SysConfigLite>()
                .in(SysConfigLite::getConfigKey,
                        List.of(CFG_ANONYMOUS_ALLOWED, CFG_DEFAULT_MINUTE, CFG_DEFAULT_HOUR, CFG_DEFAULT_DAY)));
        Map<String, String> map = new ConcurrentHashMap<>();
        for (SysConfigLite c : configs) {
            map.put(c.getConfigKey(), c.getConfigValue());
        }
        return map;
    }

    /** 匿名调用灰度开关（sys_config ratelimit.anonymous.allowed，默认 false 严格） */
    public boolean isAnonymousAllowed() {
        return "true".equalsIgnoreCase(sysConfigCache.get().getOrDefault(
                CFG_ANONYMOUS_ALLOWED, String.valueOf(sandboxConfig.getRatelimit().isAnonymousAllowedFallback())));
    }

    /**
     * 限流判定（在 ApiKey 校验通过后执行，design.md §8.3）。
     *
     * @param principal 鉴权主体（可为匿名）
     * @return 命中结果；null 表示放行
     */
    public synchronized Hit check(AuthContext.Principal principal) {
        if (principal != null && principal.isRateLimitExempt()) {
            // 白名单：跳过全部规则（FR-RATELIMIT-05）
            return null;
        }

        long now = System.currentTimeMillis();

        // 1) API_KEY 维度：滑动窗口
        if (principal != null && principal.getApiKeyId() != null) {
            List<RatelimitRule> rules = apiKeyRules.get().get(principal.getApiKeyId());
            if (rules != null) {
                for (RatelimitRule rule : rules) {
                    if (hitSlidingWindow("ak:" + principal.getApiKeyId() + ":" + rule.getWindowType(),
                            now, rule.getWindowType(), rule.getThreshold())) {
                        return new Hit(rule);
                    }
                }
            }
        }

        // 2) CLIENT 维度：令牌桶
        if (principal != null && principal.getClientId() != null) {
            List<RatelimitRule> rules = clientRules.get().get(principal.getClientId());
            if (rules != null) {
                for (RatelimitRule rule : rules) {
                    if (hitTokenBucket("cl:" + principal.getClientId() + ":" + rule.getWindowType(),
                            now, rule.getWindowType(), rule.getThreshold())) {
                        return new Hit(rule);
                    }
                }
            }
        }

        // 3) GLOBAL 维度（dimension=GLOBAL, target_id=0）+ sys_config 全局默认阈值
        //    未匹配任何专属规则的调用方（含匿名）受全局默认约束（FR-RATELIMIT-06）
        for (RatelimitRule rule : globalRules.get()) {
            if (hitSlidingWindow("gl:" + rule.getWindowType(), now, rule.getWindowType(), rule.getThreshold())) {
                return new Hit(rule);
            }
        }
        for (String window : new String[]{"MINUTE", "HOUR", "DAY"}) {
            Integer threshold = defaultThreshold(window);
            if (threshold == null || threshold <= 0) {
                continue;
            }
            if (hitSlidingWindow("gld:" + window, now, window, threshold)) {
                return new Hit(window, threshold);
            }
        }
        return null;
    }

    /** sys_config 全局默认阈值；非法或缺失回落内置默认（60/min、1000/hour、10000/day） */
    private Integer defaultThreshold(String windowType) {
        String key = switch (windowType) {
            case "MINUTE" -> CFG_DEFAULT_MINUTE;
            case "HOUR" -> CFG_DEFAULT_HOUR;
            case "DAY" -> CFG_DEFAULT_DAY;
            default -> null;
        };
        if (key == null) {
            return null;
        }
        String raw = sysConfigCache.get().get(key);
        if (raw == null) {
            return switch (windowType) {
                case "MINUTE" -> 60;
                case "HOUR" -> 1000;
                default -> 10000;
            };
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("sys_config {} 非法值 [{}]，使用内置默认", key, raw);
            return builtinDefault(windowType);
        }
    }

    private static Integer builtinDefault(String windowType) {
        return switch (windowType) {
            case "MINUTE" -> 60;
            case "HOUR" -> 1000;
            case "DAY" -> 10000;
            default -> null;
        };
    }

    // ===================== 窗口算法 =====================

    /** 滑动窗口判定：返回 true=超限 */
    private boolean hitSlidingWindow(String key, long now, String windowType, Integer threshold) {
        if (threshold == null || threshold <= 0) {
            return false;
        }
        long windowMillis = windowMillis(windowType);
        Deque<Long> deque = slidingWindows.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!deque.isEmpty() && now - deque.peekFirst() > windowMillis) {
            deque.pollFirst();
        }
        if (deque.size() >= threshold) {
            return true;
        }
        deque.addLast(now);
        return false;
    }

    /** 令牌桶判定：容量=threshold，按窗口均速回填；返回 true=无令牌可用 */
    private boolean hitTokenBucket(String key, long now, String windowType, Integer threshold) {
        if (threshold == null || threshold <= 0) {
            return false;
        }
        long windowMillis = windowMillis(windowType);
        TokenBucket bucket = tokenBuckets.computeIfAbsent(key,
                k -> new TokenBucket(threshold, threshold / (double) windowMillis, now));
        return !bucket.tryAcquire(now);
    }

    private static long windowMillis(String windowType) {
        return switch (windowType == null ? "" : windowType.toUpperCase()) {
            case "MINUTE" -> Duration.ofMinutes(1).toMillis();
            case "HOUR" -> Duration.ofHours(1).toMillis();
            case "DAY" -> Duration.ofDays(1).toMillis();
            default -> Duration.ofMinutes(1).toMillis();
        };
    }

    /** 建议重试秒数：由窗口类型推导（design.md §7.5） */
    public static long retryAfterSeconds(String windowType) {
        return Math.max(1, windowMillis(windowType) / 1000);
    }

    /** 清理长时间未使用的计数桶，避免内存膨胀 */
    @Scheduled(fixedRateString = "${sandbox.ratelimit.cleanup-interval-millis:600000}")
    public void cleanupIdleCounters() {
        long cutoff = System.currentTimeMillis() - Duration.ofDays(2).toMillis();
        slidingWindows.entrySet().removeIf(e -> {
            Deque<Long> d = e.getValue();
            return d == null || d.isEmpty() || d.peekLast() < cutoff;
        });
        tokenBuckets.entrySet().removeIf(e -> e.getValue().lastRefill < cutoff);
    }

    // ===================== data =====================

    /** 限流命中描述：ruleId 可空（null=命中 sys_config 全局默认阈值而非具体规则行） */
    @Getter
    public static class Hit {
        private final Long ruleId;
        private final String windowType;
        private final int threshold;

        Hit(RatelimitRule rule) {
            this.ruleId = rule.getId();
            this.windowType = rule.getWindowType();
            this.threshold = rule.getThreshold() == null ? 0 : rule.getThreshold();
        }

        Hit(String windowType, int threshold) {
            this.ruleId = null;
            this.windowType = windowType;
            this.threshold = threshold;
        }

        public long retryAfterSeconds() {
            return RatelimitService.retryAfterSeconds(windowType);
        }

        public String message() {
            return "Rate limit exceeded: " + threshold + " requests per " + windowType.toLowerCase();
        }
    }

    private static class TokenBucket {
        private final double capacity;
        private final double refillPerMillis;
        private double tokens;
        private long lastRefill;

        TokenBucket(int capacity, double refillPerMillis, long now) {
            this.capacity = capacity;
            this.refillPerMillis = refillPerMillis;
            this.tokens = capacity;
            this.lastRefill = now;
        }

        boolean tryAcquire(long now) {
            double elapsed = now - lastRefill;
            if (elapsed > 0) {
                tokens = Math.min(capacity, tokens + elapsed * refillPerMillis);
                lastRefill = now;
            }
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    /** 仅供测试与运维：当前缓存规则总数 */
    public int cachedRuleCount() {
        return apiKeyRules.get().values().stream().mapToInt(List::size).sum()
                + clientRules.get().values().stream().mapToInt(List::size).sum()
                + globalRules.get().size();
    }

    /** 仅供测试：清空计数器 */
    void resetCountersForTest() {
        slidingWindows.clear();
        tokenBuckets.clear();
    }
}
