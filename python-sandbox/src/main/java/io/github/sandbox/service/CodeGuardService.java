package io.github.sandbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.sandbox.config.SandboxConfig;
import io.github.sandbox.entity.SysConfigLite;
import io.github.sandbox.mapper.SysConfigLiteMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 代码危险检测策略编排（CodeGuard）。
 *
 * <p>在执行 Python 代码前串联两种互相独立、可分别开关的检测策略：</p>
 * <ol>
 *   <li><b>静态校验策略</b>：{@link PythonCodeValidator}（正则黑名单，原有方式，不删除）；</li>
 *   <li><b>模型推理策略</b>：{@link ModelCodeDetector}（调用 train/infer 微调模型推理服务）。</li>
 * </ol>
 *
 * <p>策略开关存放于 sys_config（管理端"系统设置"页面可配置、即时生效）：</p>
 * <ul>
 *   <li>{@code codeguard.static.enabled} — 静态校验开关（默认 true）</li>
 *   <li>{@code codeguard.model.enabled}  — 模型推理开关（默认 false，需显式启用）</li>
 *   <li>{@code codeguard.model.fail-open} — 推理服务不可用时是否放行（默认 true；
 *       false 则服务故障时拒绝执行，更安全但依赖推理服务可用性）</li>
 * </ul>
 *
 * <p>缓存模式与 {@link RatelimitService} 对齐：定时拉取（复用 ratelimit.refresh-interval-millis）
 * + fail-open 保留旧缓存；sys_config 缺键时回落到 {@code sandbox.code-guard.*} 本地配置。</p>
 *
 * <p>任一策略命中危险即抛出 {@link SecurityException}（HTTP 403 SECURITY_VIOLATION），
 * 两策略均放行才允许执行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGuardService {

    /** sys_config 受控键：静态校验策略开关 */
    public static final String CFG_STATIC_ENABLED = "codeguard.static.enabled";
    /** sys_config 受控键：模型推理策略开关 */
    public static final String CFG_MODEL_ENABLED = "codeguard.model.enabled";
    /** sys_config 受控键：模型推理失败降级策略（true=放行 fail-open，false=拒绝 fail-close） */
    public static final String CFG_MODEL_FAIL_OPEN = "codeguard.model.fail-open";

    private final PythonCodeValidator pythonCodeValidator;
    private final ModelCodeDetector modelCodeDetector;
    private final SysConfigLiteMapper sysConfigLiteMapper;
    private final SandboxConfig sandboxConfig;

    /** sys_config 策略开关快照 */
    private final AtomicReference<Map<String, String>> sysConfigCache = new AtomicReference<>(Map.of());

    @PostConstruct
    public void init() {
        // 启动加载：同步拉取一次；失败不阻断启动（保留空缓存由定时任务兜底，缺键走本地回落值）
        safeRefresh();
    }

    /** 定时刷新策略开关（复用限流刷新周期，默认 60s） */
    @Scheduled(fixedRateString = "${sandbox.ratelimit.refresh-interval-millis:60000}")
    public void scheduledRefresh() {
        safeRefresh();
    }

    /**
     * 立即重拉策略开关（供内部 reload 或管理端联动调用）。
     *
     * @return true=刷新成功；false=拉取失败（保留旧缓存）
     */
    public boolean reload() {
        return safeRefresh();
    }

    private synchronized boolean safeRefresh() {
        try {
            List<SysConfigLite> configs = sysConfigLiteMapper.selectList(
                    new LambdaQueryWrapper<SysConfigLite>()
                            .in(SysConfigLite::getConfigKey,
                                    List.of(CFG_STATIC_ENABLED, CFG_MODEL_ENABLED, CFG_MODEL_FAIL_OPEN)));
            Map<String, String> map = new ConcurrentHashMap<>();
            for (SysConfigLite c : configs) {
                map.put(c.getConfigKey(), c.getConfigValue());
            }
            sysConfigCache.set(map);
            log.info("CodeGuard 策略开关刷新成功: static={} model={} failOpen={}",
                    isStaticEnabled(), isModelEnabled(), isModelFailOpen());
            return true;
        } catch (Exception e) {
            // 拉取失败：保留旧缓存（fail-open），由定时拉取兜底
            log.error("CodeGuard 策略开关拉取失败，保留旧缓存: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 执行前检测入口：按当前策略开关依次执行静态校验与模型推理。
     *
     * @param code 待执行的 Python 源码
     * @throws SecurityException 任一策略判定危险（或模型策略 fail-close 且服务不可用）
     */
    public void guard(String code) {
        if (code == null || code.trim().isEmpty()) {
            return;
        }

        // 策略1：静态校验（原有方式，开关独立）
        if (isStaticEnabled()) {
            pythonCodeValidator.validate(code);
        }

        // 策略2：模型推理检测
        if (isModelEnabled()) {
            try {
                boolean dangerous = modelCodeDetector.isDangerous(code);
                if (dangerous) {
                    log.warn("Blocked by model detection: code judged DANGEROUS");
                    throw new SecurityException(
                            "Code classified as dangerous by AI model"
                                    + " [VIOLATION: MODEL_DETECTED_DANGEROUS]");
                }
            } catch (ModelCodeDetector.DetectionUnavailableException e) {
                if (isModelFailOpen()) {
                    log.warn("Model detection service unavailable, fail-open (allow): {}", e.getMessage());
                } else {
                    log.error("Model detection service unavailable, fail-close (reject): {}", e.getMessage());
                    throw new SecurityException(
                            "Model detection service unavailable and fail-close policy is on"
                                    + " [VIOLATION: MODEL_DETECTION_UNAVAILABLE]");
                }
            }
        }
    }

    private boolean isStaticEnabled() {
        return "true".equalsIgnoreCase(sysConfigCache.get().getOrDefault(
                CFG_STATIC_ENABLED,
                String.valueOf(sandboxConfig.getCodeGuard().isStaticEnabledFallback())));
    }

    private boolean isModelEnabled() {
        return "true".equalsIgnoreCase(sysConfigCache.get().getOrDefault(
                CFG_MODEL_ENABLED,
                String.valueOf(sandboxConfig.getCodeGuard().isModelEnabledFallback())));
    }

    private boolean isModelFailOpen() {
        return "true".equalsIgnoreCase(sysConfigCache.get().getOrDefault(
                CFG_MODEL_FAIL_OPEN,
                String.valueOf(sandboxConfig.getCodeGuard().isModelFailOpenFallback())));
    }
}
