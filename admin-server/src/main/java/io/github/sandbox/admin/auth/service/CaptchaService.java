package io.github.sandbox.admin.auth.service;

import com.wf.captcha.ArithmeticCaptcha;
import io.github.sandbox.admin.auth.dto.CaptchaVO;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.UUID;

/**
 * 图形验证码服务（T-0013，design.md §4.3，Easy-Captcha）。
 *
 * <ul>
 *   <li>答案存储：Redis 键 {@code admin:captcha:{captchaId}}（命名空间与 python-sandbox 隔离），TTL 5 分钟。</li>
 *   <li>一次性消费：校验后立即删除键（无论成败），杜绝重放。</li>
 *   <li>验证码错误只返回验证码业务错误（11001），<b>不消耗账号登录失败次数</b>——
 *       失败计数与验证码存储完全分离（design.md §4.5）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class CaptchaService {

    /** 验证码 Redis 命名空间（admin: 前缀，design.md §3.2） */
    public static final String CAPTCHA_KEY_PREFIX = "admin:captcha:";

    /** TTL 与前端倒计时一致（design.md §4.3） */
    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate stringRedisTemplate;

    /** 生成算术型图形验证码（+、-、*），答案存 Redis */
    public CaptchaVO generate() {
        ArithmeticCaptcha captcha = new ArithmeticCaptcha(150, 48);
        captcha.setLen(2);

        String captchaId = UUID.randomUUID().toString().replace("-", "");
        // ArithmeticCaptcha.text() 返回计算结果字符串（答案）
        String answer = captcha.text();
        stringRedisTemplate.opsForValue().set(CAPTCHA_KEY_PREFIX + captchaId, answer.toLowerCase(), CAPTCHA_TTL);

        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaId(captchaId);
        vo.setImg(captcha.toBase64());
        vo.setExpireSeconds(CAPTCHA_TTL.toSeconds());
        return vo;
    }

    /**
     * 一次性消费校验：取出即删除。
     *
     * @return 校验通过返回 true；验证码错误/过期返回 false（由调用方决定是否计入失败）
     */
    public boolean verifyAndConsume(String captchaId, String userAnswer) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(userAnswer)) {
            return false;
        }
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        String answer = stringRedisTemplate.opsForValue().get(key);
        if (answer == null) {
            return false;
        }
        // 一次性消费：无论成败均删除
        stringRedisTemplate.delete(key);
        return answer.equalsIgnoreCase(userAnswer.trim());
    }

    /** 校验失败统一抛业务异常（登录调用入口用） */
    public void assertValid(String captchaId, String userAnswer) {
        if (!verifyAndConsume(captchaId, userAnswer)) {
            throw new BusinessException(ErrorCode.CAPTCHA_ERROR);
        }
    }
}
