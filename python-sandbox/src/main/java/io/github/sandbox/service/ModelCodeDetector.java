package io.github.sandbox.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.sandbox.config.SandboxConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 模型推理危险代码检测客户端。
 *
 * <p>调用 train/infer 提供的推理服务（transformers 版 {@code api_server.py} 或
 * vLLM 版 {@code vllm_api_server.py}，接口形态一致：POST /detect，
 * 请求体 {@code {"code": "..."}}，响应 {@code {"label": "SAFE|DANGEROUS", "raw_output": "..."}}），
 * 使用基于 Qwen2.5-Coder 微调的模型判断代码片段是否包含危险系统操作。</p>
 *
 * <p>本组件只负责"问一次、判一次"，策略开关与失败降级由 {@link CodeGuardService} 编排。
 * HTTP 异常/超时/非 200 响应统一抛出 {@link DetectionUnavailableException}，由上层决定 fail-open 或 fail-close。</p>
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

    private final SandboxConfig config;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ModelCodeDetector(SandboxConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getCodeGuard().getDetectTimeoutMillis()))
                .build();
    }

    /**
     * 调用推理服务检测代码是否危险。
     *
     * @param code 待检测的 Python 源码
     * @return true = 模型判定 DANGEROUS；false = 模型判定 SAFE
     * @throws DetectionUnavailableException 推理服务不可用
     */
    public boolean isDangerous(String code) {
        SandboxConfig.CodeGuard guard = config.getCodeGuard();
        long start = System.currentTimeMillis();
        try {
            String body = objectMapper.writeValueAsString(Map.of("code", code));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(guard.getDetectBaseUrl()) + "/detect"))
                    .timeout(Duration.ofMillis(guard.getDetectTimeoutMillis()))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() != 200) {
                throw new DetectionUnavailableException(
                        "detect service returned HTTP " + response.statusCode(), null);
            }

            JsonNode json = objectMapper.readTree(response.body());
            String label = json.path("label").asText("");
            long elapsed = System.currentTimeMillis() - start;
            log.debug("model detect done in {}ms, label={}, raw={}",
                    elapsed, label, json.path("raw_output").asText(""));
            return label.toUpperCase().contains("DANGEROUS");
        } catch (DetectionUnavailableException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DetectionUnavailableException("detect call interrupted", e);
        } catch (Exception e) {
            throw new DetectionUnavailableException("detect call failed: " + e.getMessage(), e);
        }
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
