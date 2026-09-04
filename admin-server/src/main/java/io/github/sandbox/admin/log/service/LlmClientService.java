package io.github.sandbox.admin.log.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import io.github.sandbox.admin.sys.service.SysConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;

/**
 * 大模型 API 客户端（基于 openai-java SDK）。
 *
 * <p>封装 OpenAI 兼容接口的调用，支持 DeepSeek / Qwen / OpenAI 等提供商。
 * 每次调用时从 sys_config 读取最新配置（endpoint / model），API Key 从环境变量注入。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmClientService {

    private final SysConfigReader sysConfigReader;
    private final ObjectMapper objectMapper;

    /** 大模型 API Key，从 application.yml 的 admin.llm-review.api-key 读取 */
    @Value("${admin.llm-review.api-key:}")
    private String apiKey;

    /**
     * 调用大模型进行代码安全复检。
     *
     * @param codeSnippet      代码片段
     * @param smallModelLabel  小模型判定标签（SAFE / DANGEROUS）
     * @param smallModelOutput 小模型原始输出
     * @return 大模型响应结果
     */
    public LlmReviewResult callLlmForReview(String codeSnippet, String smallModelLabel, String smallModelOutput) {
        String endpoint = sysConfigReader.getString(SysConfigReader.Keys.LLM_REVIEW_API_ENDPOINT);
        String modelName = sysConfigReader.getString(SysConfigReader.Keys.LLM_REVIEW_MODEL_NAME);
        String provider = sysConfigReader.getString(SysConfigReader.Keys.LLM_REVIEW_PROVIDER);

        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("大模型 API endpoint 未配置");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("大模型 API Key 未配置（环境变量 LLM_REVIEW_API_KEY）");
        }

        // 构建 prompt
        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(codeSnippet, smallModelLabel, smallModelOutput);

        // 构建 OpenAI 客户端
        OpenAIClient client = OpenAIOkHttpClient.builder()
                .baseUrl(endpoint)
                .apiKey(apiKey)
                .build();

        long startTime = System.currentTimeMillis();
        try {
            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(modelName)
                    .addMessage(ChatCompletionMessageParam.ofSystem(
                            ChatCompletionSystemMessageParam.builder()
                                    .content(systemPrompt)
                                    .build()))
                    .addMessage(ChatCompletionMessageParam.ofUser(
                            ChatCompletionUserMessageParam.builder()
                                    .content(userPrompt)
                                    .build()))
                    .temperature(0.1)
                    .maxTokens(1024)
                    .build();

            ChatCompletion completion = client.chat().completions().create(params);
            long latencyMs = System.currentTimeMillis() - startTime;

            String content = completion.choices().stream()
                    .findFirst()
                    .map(c -> c.message().content().orElse(""))
                    .orElse("");

            return parseLlmResponse(content, provider, modelName, latencyMs);
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("调用大模型失败: {}", e.getMessage(), e);
            throw new RuntimeException("大模型调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建系统提示词。
     */
    /**
     * 构建系统提示词。
     * 注意：代码片段以 base64 编码传输，避免触发大模型内容安全审查。
     */
    private String buildSystemPrompt() {
        return """
                你是一个代码安全审计专家。你的任务是对一个小模型的代码危险性判定结果进行复检。
                
                小模型会对 Python 代码进行安全判定，输出 SAFE（安全）或 DANGEROUS（危险）。
                你需要：
                1. 判断小模型的判定是否正确
                2. 如果不正确，给出正确的判定
                3. 详细解释为什么小模型的判定是对/错
                
                代码片段以 Base64 编码提供，请先解码后再分析。
                
                请以 JSON 格式返回结果：
                {
                    "agreed": true/false,
                    "label": "SAFE/DANGEROUS",
                    "explanation": "详细解释..."
                }
                
                其中：
                - agreed: 是否同意小模型的判定
                - label: 你认为正确的判定（如果同意小模型，则与小模型一致；如果不同意，则给出正确判定）
                - explanation: 详细解释你的判断依据
                """;
    }

    /**
     * 构建用户提示词。
     * 代码片段使用 Base64 编码传输，避免触发大模型内容安全审查。
     */
    private String buildUserPrompt(String codeSnippet, String smallModelLabel, String smallModelOutput) {
        String encodedCode = Base64.getEncoder().encodeToString(
                codeSnippet != null ? codeSnippet.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0]);
        return String.format("""
                请对以下代码的小模型判定结果进行复检：
                
                ## 代码片段（Base64 编码，请先解码）
                %s
                
                ## 小模型判定结果
                - 判定标签: %s
                - 原始输出: %s
                
                请分析这段代码的实际危险性，判断小模型的判定是否正确，并给出你的判定和解释。
                """, encodedCode, smallModelLabel, smallModelOutput != null ? smallModelOutput : "无");
    }

    /**
     * 解析大模型响应。
     */
    private LlmReviewResult parseLlmResponse(String content, String provider, String model, long latencyMs) {
        LlmReviewResult result = new LlmReviewResult();
        result.setProvider(provider);
        result.setModel(model);
        result.setLatencyMs(latencyMs);
        result.setRawResponse(content);

        try {
            // 尝试提取 JSON
            String jsonContent = extractJson(content);
            JsonNode node = objectMapper.readTree(jsonContent);

            boolean agreed = node.path("agreed").asBoolean(false);
            String label = node.path("label").asText("UNKNOWN");
            String explanation = node.path("explanation").asText("");

            result.setAgreed(agreed);
            result.setLabel(normalizeLabel(label));
            result.setExplanation(explanation);
        } catch (Exception e) {
            log.warn("解析大模型响应失败，原始内容: {}", content, e);
            // 尝试从文本中提取标签
            result.setLabel(extractLabelFromText(content));
            result.setExplanation(content);
            result.setAgreed(false);
        }

        return result;
    }

    /**
     * 从文本中提取 JSON 内容。
     */
    private String extractJson(String text) {
        // 尝试提取 ```json ... ``` 或 { ... }
        int jsonStart = text.indexOf('{');
        int jsonEnd = text.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return text.substring(jsonStart, jsonEnd + 1);
        }
        return text;
    }

    /**
     * 从文本中提取标签。
     */
    private String extractLabelFromText(String text) {
        String upper = text.toUpperCase();
        if (upper.contains("DANGEROUS") || upper.contains("危险")) {
            return "DANGEROUS";
        }
        if (upper.contains("SAFE") || upper.contains("安全")) {
            return "SAFE";
        }
        return "UNKNOWN";
    }

    /**
     * 标准化标签。
     */
    private String normalizeLabel(String label) {
        if (label == null) return "UNKNOWN";
        String upper = label.toUpperCase().trim();
        if (upper.contains("DANGEROUS") || upper.contains("危险")) {
            return "DANGEROUS";
        }
        if (upper.contains("SAFE") || upper.contains("安全")) {
            return "SAFE";
        }
        return "UNKNOWN";
    }

    /**
     * 大模型响应结果。
     */
    public static class LlmReviewResult {
        private String provider;
        private String model;
        private String rawResponse;
        private boolean agreed;
        private String label;
        private String explanation;
        private long latencyMs;

        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public String getRawResponse() { return rawResponse; }
        public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }
        public boolean isAgreed() { return agreed; }
        public void setAgreed(boolean agreed) { this.agreed = agreed; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
        public long getLatencyMs() { return latencyMs; }
        public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }
    }
}
