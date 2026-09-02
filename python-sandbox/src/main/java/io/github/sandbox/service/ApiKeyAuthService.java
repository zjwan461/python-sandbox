package io.github.sandbox.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.sandbox.context.AuthContext;
import io.github.sandbox.entity.AdminUserLite;
import io.github.sandbox.entity.ClientApiKey;
import io.github.sandbox.entity.ClientApp;
import io.github.sandbox.mapper.AdminUserLiteMapper;
import io.github.sandbox.mapper.ClientApiKeyMapper;
import io.github.sandbox.mapper.ClientAppMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * ApiKey 查表认证服务（T-0023，design.md §8.2）。
 *
 * <p>校验链路：明文 → SHA-256(hex 小写) → client_api_key.key_hash 查表 →
 * 状态/有效期 → 客户端启用 → 归属用户启用。
 * 失败语义与 admin-server ErrorCode 30001~30005 口径一致：</p>
 * <ul>
 *   <li>{@link AuthResult#API_KEY_MISSING} → 30001（Header 缺失/空）</li>
 *   <li>{@link AuthResult#API_KEY_NOT_FOUND} → 30002（摘要无匹配记录）</li>
 *   <li>{@link AuthResult#API_KEY_INVALID} → 30003（停用/撤销/过期/未生效）</li>
 *   <li>{@link AuthResult#CLIENT_DISABLED} → 30004</li>
 *   <li>{@link AuthResult#USER_DISABLED} → 30005（ApiKey 绑定用户或客户端归属用户被停用）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyAuthService {

    /** 认证 Header（沿用既有 SDK 约定） */
    public static final String API_KEY_HEADER = "X-Api-Key";

    private final ClientApiKeyMapper clientApiKeyMapper;
    private final ClientAppMapper clientAppMapper;
    private final AdminUserLiteMapper adminUserLiteMapper;

    /**
     * 认证结果。code 与错误消息口径对齐批次3 admin-server ErrorCode（30001~30005）。
     */
    @Getter
    public static class AuthResult {
        public static final int OK = 0;
        public static final int API_KEY_MISSING = 30001;
        public static final int API_KEY_NOT_FOUND = 30002;
        public static final int API_KEY_INVALID = 30003;
        public static final int CLIENT_DISABLED = 30004;
        public static final int USER_DISABLED = 30005;

        private final int code;
        private final String errorCode;
        private final String message;
        private final AuthContext.Principal principal;

        private AuthResult(int code, String errorCode, String message, AuthContext.Principal principal) {
            this.code = code;
            this.errorCode = errorCode;
            this.message = message;
            this.principal = principal;
        }

        public boolean isSuccess() {
            return code == OK;
        }
    }

    /**
     * 校验 ApiKey 明文并生成鉴权主体。
     *
     * @param plainKey 请求 Header 携带的明文（sk_live_ + 40hex 形态）；可为 null
     * @return 认证结果；成功时 principal 携带 clientId/apiKeyId/ownerUserId
     */
    public AuthResult authenticate(String plainKey) {
        if (plainKey == null || plainKey.isBlank()) {
            return new AuthResult(AuthResult.API_KEY_MISSING, "API_KEY_MISSING",
                    "Missing ApiKey header: " + API_KEY_HEADER, null);
        }

        String hash = sha256Hex(plainKey.trim());
        ClientApiKey apiKey = clientApiKeyMapper.selectOne(new LambdaQueryWrapper<ClientApiKey>()
                .eq(ClientApiKey::getKeyHash, hash)
                .last("LIMIT 1"));
        if (apiKey == null) {
            return new AuthResult(AuthResult.API_KEY_NOT_FOUND, "API_KEY_NOT_FOUND",
                    "ApiKey not found", null);
        }

        LocalDateTime now = LocalDateTime.now();
        // 状态机：1=启用 2=停用 3=已过期 4=已撤销；停用/撤销/过期/未生效统一 API_KEY_INVALID（30003）
        if (apiKey.getStatus() == null || apiKey.getStatus() != 1
                || apiKey.getEffectiveTime() != null && apiKey.getEffectiveTime().isAfter(now)
                || apiKey.getExpireTime() != null && !apiKey.getExpireTime().isAfter(now)) {
            return new AuthResult(AuthResult.API_KEY_INVALID, "API_KEY_INVALID",
                    "ApiKey is disabled, revoked, expired or not yet effective", null);
        }

        ClientApp client = clientAppMapper.selectById(apiKey.getClientId());
        if (client == null || client.getStatus() == null || client.getStatus() != 1) {
            return new AuthResult(AuthResult.CLIENT_DISABLED, "CLIENT_DISABLED",
                    "Client application is disabled", null);
        }

        // 归属用户口径：COALESCE(bound_user_id, client_app.owner_user_id)
        Long ownerUserId = apiKey.getBoundUserId() != null ? apiKey.getBoundUserId() : client.getOwnerUserId();
        if (ownerUserId != null) {
            AdminUserLite user = adminUserLiteMapper.selectById(ownerUserId);
            // 用户被停用或软删除（逻辑删除查不到）→ USER_DISABLED
            if (user == null || user.getStatus() == null || user.getStatus() != 1) {
                return new AuthResult(AuthResult.USER_DISABLED, "USER_DISABLED",
                        "Owner user is disabled", null);
            }
        }

        AuthContext.Principal principal = AuthContext.Principal.builder()
                .clientId(apiKey.getClientId())
                .apiKeyId(apiKey.getId())
                .ownerUserId(ownerUserId)
                .anonymous(false)
                .rateLimitExempt(apiKey.getRateLimitExempt() != null && apiKey.getRateLimitExempt() == 1)
                .build();
        return new AuthResult(AuthResult.OK, null, null, principal);
    }

    /** 构造匿名放行主体（灰度开关开启时，仅应用全局默认限流） */
    public AuthResult anonymousPass() {
        AuthContext.Principal principal = AuthContext.Principal.builder()
                .anonymous(true)
                .rateLimitExempt(false)
                .build();
        return new AuthResult(AuthResult.OK, null, null, principal);
    }

    /** SHA-256 hex 小写（与 client_api_key.key_hash 生成口径一致） */
    public static String sha256Hex(String plain) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(plain.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
