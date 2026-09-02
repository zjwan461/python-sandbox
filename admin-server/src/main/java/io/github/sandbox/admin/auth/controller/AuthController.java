package io.github.sandbox.admin.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import io.github.sandbox.admin.auth.dto.ChangePasswordRequest;
import io.github.sandbox.admin.auth.dto.CaptchaVO;
import io.github.sandbox.admin.auth.dto.LoginRequest;
import io.github.sandbox.admin.auth.dto.LoginResultVO;
import io.github.sandbox.admin.auth.dto.WhoamiVO;
import io.github.sandbox.admin.auth.service.AuthService;
import io.github.sandbox.admin.auth.service.CaptchaService;
import io.github.sandbox.admin.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口（design.md §10.3：/admin-api/auth）。
 *
 * <p>context-path 已为 /admin-api，故路径为 /auth/**。
 * 白名单：/auth/captcha、/auth/login；/auth/logout、/auth/whoami、/auth/password 需登录。</p>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;

    /** 图形验证码（免登录） */
    @GetMapping("/captcha")
    public R<CaptchaVO> captcha() {
        return R.ok(captchaService.generate());
    }
    /** 账号密码 + 验证码登录（免登录）；响应透出首次登录强制改密标记；勾选记住我下发 HttpOnly Cookie（T-0034） */
    @PostMapping("/login")
    public R<LoginResultVO> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        return R.ok(authService.login(request, response));
    }

    /**
     * 自动续登（免登录，T-0034）：前端启动时携带 HttpOnly Cookie 调用，
     * 长期 token 有效则换取新的短期 token；无效返回 data=null（不报错，前端据此跳登录页）。
     */
    @PostMapping("/auto-login")
    public R<LoginResultVO> autoLogin(HttpServletRequest request, HttpServletResponse response) {
        return R.ok(authService.autoLogin(request, response));
    }

    /** 注销（同时作废 Remember-Me 长期 token，T-0034） */
    @PostMapping("/logout")
    public R<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return R.ok();
    }

    /** 当前用户信息：用户 + 角色 + 按钮权限码集合 */
    @GetMapping("/whoami")
    public R<WhoamiVO> whoami() {
        return R.ok(authService.whoami());
    }

    /** 修改自身密码（校验旧密码；成功后旧会话全部作废） */
    @PutMapping("/password")
    public R<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return R.ok();
    }

    /** 当前 token 剩余有效期（前端会话倒计时用） */
    @GetMapping("/token-ttl")
    public R<Long> tokenTtl() {
        return R.ok(StpUtil.getTokenTimeout());
    }
}
