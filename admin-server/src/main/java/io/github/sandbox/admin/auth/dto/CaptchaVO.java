package io.github.sandbox.admin.auth.dto;

import lombok.Data;

/**
 * 验证码响应（T-0013）：captchaId + base64 图像，供登录页消费。
 */
@Data
public class CaptchaVO {

    /** 验证码标识（Redis 键 admin:captcha:{captchaId}） */
    private String captchaId;

    /** base64 图像（data:image/png;base64,...） */
    private String img;

    /** 有效期（秒），前端倒计时用 */
    private long expireSeconds;
}
