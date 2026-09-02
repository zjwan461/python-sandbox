package io.github.sandbox.admin.apikey.util;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * ApiKey 生成器（T-0029，design.md §6.2）。
 *
 * <p>明文形态：{@code sk_live_ + 40 hex}（SecureRandom）。落库仅保留：</p>
 * <ul>
 *   <li>{@code key_hash} = SHA-256(明文) hex 小写（python-sandbox 侧按摘要查表认证）</li>
 *   <li>{@code key_prefix} = 明文前 12 位（如 {@code sk_live_a1b2}）</li>
 *   <li>{@code key_suffix_mask} = 明文后 4 位</li>
 * </ul>
 * <p>明文只存活于生成瞬间的返回值，不进入 Redis、不进入日志、不入库（默认决策 #1）。
 * 注意：本工具类的 toString 已被覆写，明文不会被审计切面的 args 摘要带出。</p>
 */
public final class ApiKeyGenerator {

    /** 业务前缀，外部识别用 */
    public static final String PLAINTEXT_PREFIX = "sk_live_";

    /** 随机段长度（hex 字符数） */
    private static final int RANDOM_HEX_LEN = 40;

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyGenerator() {
    }

    /** 生成结果：明文（一次性）+ 持久化三元材料 */
    @Data
    @AllArgsConstructor
    public static class Generated {
        /** 明文（仅一次性展示，调用方负责用后即弃；禁止任何途径落库/落日志） */
        private final String plaintext;
        /** SHA-256 hex 小写（64 字符），认证唯一依据 */
        private final String keyHash;
        /** 密钥前缀（明文前 12 位） */
        private final String keyPrefix;
        /** 后 4 位掩码 */
        private final String keySuffixMask;

        /** 覆写 toString：任何日志/审计摘要中不透出明文与摘要 */
        @Override
        public String toString() {
            return "Generated{keyPrefix='" + keyPrefix + "', keySuffixMask='" + keySuffixMask + "'}";
        }
    }

    /** 生成一枚全新 ApiKey（明文 + 摘要 + 前缀 + 掩码） */
    public static Generated generate() {
        StringBuilder hex = new StringBuilder(RANDOM_HEX_LEN);
        byte[] buf = new byte[RANDOM_HEX_LEN / 2];
        RANDOM.nextBytes(buf);
        for (byte b : buf) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        String plaintext = PLAINTEXT_PREFIX + hex;
        return new Generated(plaintext, sha256Hex(plaintext),
                plaintext.substring(0, Math.min(12, plaintext.length())),
                plaintext.substring(plaintext.length() - 4));
    }

    /** 计算明文的 SHA-256 hex 小写摘要（python-sandbox 侧认证口径一致） */
    public static String sha256Hex(String plaintext) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
