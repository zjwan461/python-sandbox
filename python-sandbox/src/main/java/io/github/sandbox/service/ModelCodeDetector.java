package io.github.sandbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import io.github.sandbox.config.SandboxConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 模型推理危险代码检测客户端。
 *
 * <p>
 * 调用 train/infer 提供的推理服务（transformers 版 {@code api_server.py} 或
 * vLLM 版 {@code vllm_api_server.py}，接口形态一致：POST /detect，
 * 请求体 {@code {"code": "..."}}，响应
 * {@code {"label": "SAFE|DANGEROUS", "raw_output": "..."}}），
 * 使用基于 Qwen2.5-Coder 微调的模型判断代码片段是否包含危险系统操作。
 * </p>
 *
 * <p>
 * 返回 {@link DetectionResult} 完整结果（label / raw_output / 耗时），
 * 供上层 {@link CodeGuardService} 编排决策并写入 codeguard_detect_log 记录。
 * HTTP 异常/超时/非 200 响应统一抛出 {@link DetectionUnavailableException}，
 * 由上层决定 fail-open 或 fail-close。
 * </p>
 */
@Slf4j
@Component
public class ModelCodeDetector {

    /** 推理服务不可用（网络失败、超时、非 200、响应结构异常） */
    public static class DetectionUnavailableException extends RuntimeException {
        public DetectionUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 单次检测完整结果（用于审计与再训练数据回流） */
    @Data
    public static class DetectionResult {
        /** 判定标签：SAFE / DANGEROUS */
        private String label;
        /** 模型原始输出文本 */
        private String rawOutput;
        /** 是否判定为危险 */
        private boolean dangerous;
        /** HTTP 调用耗时（毫秒） */
        private long latencyMs;
    }

    private final SandboxConfig config;
    private final ObjectMapper objectMapper;

    public ModelCodeDetector(SandboxConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    /**
     * 调用推理服务检测代码是否危险。
     *
     * @param code 待检测的 Python 源码
     * @return 完整检测结果（label/raw_output/耗时）
     * @throws DetectionUnavailableException 推理服务不可用
     */
    public DetectionResult detect(String code) {
        SandboxConfig.CodeGuard guard = config.getCodeGuard();
        long start = System.currentTimeMillis();
        try {
            String body = objectMapper.writeValueAsString(Map.of("code", code));

            HttpRequest request = HttpUtil
                    .createPost(trimTrailingSlash(guard.getDetectBaseUrl()) + "/detect");
            request.timeout(config.getCodeGuard().getDetectTimeoutMillis())
                    .header("Content-Type", "application/json")
                    .body(body);
            HttpResponse response = request.execute();
            if (response.getStatus() != 200) {
                throw new DetectionUnavailableException(
                        "detect service returned HTTP " + response.getStatus() + " with http body: " + response.body(),
                        null);
            }

            JsonNode json = objectMapper.readTree(response.body());
            DetectionResult result = new DetectionResult();
            result.setLabel(json.path("label").asText(""));
            result.setRawOutput(json.path("raw_output").asText(""));
            result.setDangerous(result.getLabel().toUpperCase().contains("DANGEROUS"));
            result.setLatencyMs(System.currentTimeMillis() - start);
            log.debug("model detect done in {}ms, label={}, raw={}",
                    result.getLatencyMs(), result.getLabel(), result.getRawOutput());
            return result;
        } catch (DetectionUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new DetectionUnavailableException("detect call failed: " + e.getMessage(), e);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
