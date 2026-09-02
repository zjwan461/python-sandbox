package io.github.sandbox.admin.auth.service;

import io.github.sandbox.admin.sys.service.SysConfigReader;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Remember-Me 长期 token 服务（T-0034，FR-AUTH-03；design.md §4.2、§4.5、§11.1）。
 *
 * <p>与短期 Sa-Token token 完全分离的独立通道：</p>
 * <ul>
 *   <li>长期 token 存 Redis（{@code admin:remember:token:{token} -> userId}，
 *       TTL=系统设置"最长免登天数"；{@code admin:remember:user:{userId} -> token} 反向索引，
 *       再签发时作废旧 token），命名空间与 python-sandbox 隔离。</li>
 *   <li>token 只经 HttpOnly Cookie（{@link #COOKIE_NAME}）下发与携带，
 *       前端脚本不可读取（验收：不写入 Pinia/localStorage 等可被脚本读取的存储）。</li>
 *   <li>主动退出、改密、重置密码、停用即刻使长期 token 失效；超 TTL Redis 自动过期。</li>
 *   <li>客户端 ApiKey 绝不进入本通道（默认决策 #9 的通道分离）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RememberMeService {

    /** 长期 token Cookie 名（HttpOnly，前端不可读） */
    public static final String COOKIE_NAME = "admin_remember";

    private static final String REDIS_TOKEN_KEY = "admin:remember:token:";
    private static final String REDIS_USER_KEY = "admin:remember:user:";

    private final StringRedisTemplate redisTemplate;
    private final SysConfigReader sysConfigReader;

    /**
     * 签发（或轮换）长期 token 并写入 HttpOnly Cookie。
     * 同一用户再次"记住我"登录时旧 token 立即作废（配合禁止多端在线口径）。
     */
    public void issue(Long userId, HttpServletResponse response) {
        revoke(userId); // 作废旧长期 token（反向索引存在时）
        String token = UUID.randomUUID().toString().replace("-", "");
        int days = Math.max(1, sysConfigReader.rememberMeMaxDays());
        Duration ttl = Duration.ofDays(days);
        redisTemplate.opsForValue().set(REDIS_TOKEN_KEY + token, String.valueOf(userId), ttl);
        redisTemplate.opsForValue().set(REDIS_USER_KEY + userId, token, ttl);
        writeCookie(token, days * 86400, response);
        log.info("Remember-Me 长期 token 已签发 userId={} 有效天数={}", userId, days);
    }

    /** 校验 Cookie 中的长期 token，返回 userId；缺失/无效/过期返回 null（不抛异常） */
    public Long verify(HttpServletRequest request) {
        String token = readCookie(request);
        if (token == null || token.isBlank()) {
            return null;
        }
        String userId = redisTemplate.opsForValue().get(REDIS_TOKEN_KEY + token);
        if (userId == null) {
            return null;
        }
        // 反向索引一致性：token 必须仍是该用户的当前有效 token（防止已轮换 token 重放）
        String current = redisTemplate.opsForValue().get(REDIS_USER_KEY + userId);
        return token.equals(current) ? Long.valueOf(userId) : null;
    }

    /** 续登成功后滚动刷新：换新 token 并重置 TTL（旧 token 即刻失效） */
    public void refresh(Long userId, HttpServletResponse response) {
        issue(userId, response);
    }

    /** 作废指定用户的长期 token（主动退出/改密/重置/停用联动），并清 Cookie */
    public void revoke(Long userId) {
        try {
            String old = redisTemplate.opsForValue().get(REDIS_USER_KEY + userId);
            if (old != null) {
                redisTemplate.delete(REDIS_TOKEN_KEY + old);
            }
            redisTemplate.delete(REDIS_USER_KEY + userId);
        } catch (Exception e) {
            log.warn("作废 Remember-Me token 失败 userId={}: {}", userId, e.getMessage());
        }
    }

    /** 登出/失效时清除浏览器 Cookie */
    public void clearCookie(HttpServletResponse response) {
        writeCookie("", 0, response);
    }

    // ===================== internal =====================

    private void writeCookie(String token, int maxAgeSeconds, HttpServletResponse response) {
        Cookie cookie = new Cookie(COOKIE_NAME, token);
        cookie.setHttpOnly(true);   // 脚本不可读（T-0034 验收）
        cookie.setPath("/");        // 覆盖 /admin-api 前缀，Axios withCredentials 自动携带
        cookie.setMaxAge(maxAgeSeconds);
        // 生产 HTTPS 时经容器/网关追加 Secure；本设计不展开部署层，Secure 不在应用侧强制
        response.addCookie(cookie);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
