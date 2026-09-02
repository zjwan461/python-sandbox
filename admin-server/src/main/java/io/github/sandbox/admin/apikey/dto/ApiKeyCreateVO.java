package io.github.sandbox.admin.apikey.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * ApiKey 创建/重新生成响应（T-0029，FR-APIKEY-02，默认决策 #1）。
 *
 * <p>本对象是管理端全链路中明文唯一一次随响应出网的载体；
 * {@code plaintext} 字段覆写于 toString，确保审计切面/日志摘要不透出明文。</p>
 */
@Data
public class ApiKeyCreateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 新建（或重新生成）后的 ApiKey 元数据 */
    private ApiKeyVO apiKey;

    /** 明文（仅此一次展示；不入库、不入 Redis、不入日志） */
    private String plaintext;

    /** 提示语：前端一次性展示页使用 */
    private String notice;

    /** 覆写 toString：任何日志/审计摘要中不透出明文 */
    @Override
    public String toString() {
        return "ApiKeyCreateVO{apiKeyId=" + (apiKey == null ? null : apiKey.getId()) + "}";
    }
}
