package io.github.sandbox.admin.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.sandbox.admin.audit.entity.AdminLoginLog;
import io.github.sandbox.admin.audit.mapper.AdminLoginLogMapper;
import io.github.sandbox.admin.auth.dto.ChangePasswordRequest;
import io.github.sandbox.admin.auth.dto.LoginRequest;
import io.github.sandbox.admin.auth.dto.LoginResultVO;
import io.github.sandbox.admin.auth.dto.WhoamiVO;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.security.AdminLoginUser;
import io.github.sandbox.admin.rbac.entity.AdminUser;
import io.github.sandbox.admin.rbac.mapper.AdminUserMapper;
import io.github.sandbox.admin.rbac.service.AdminPermissionAssembler;
import io.github.sandbox.admin.sys.service.SysConfigReader;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 认证闭环服务（T-0015/T-0017/T-0034，design.md §4.2/§4.3/§11.1）。
 *
 * <p>登录流程：验证码一次性校验（错误不消耗账号失败次数）→ 账号状态/锁定检查 →
 * BCrypt 密码校验（失败递增 login_fail_count，达阈值锁定 login.lock.minutes 分钟）→
 * Sa-Token 签发（is-concurrent=false 后登踢先登）→ 登录日志落库 →
 * 首次登录强制改密标记透出 →（可选）Remember-Me 长期 token 签发（T-0034）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final CaptchaService captchaService;
    private final AdminUserMapper adminUserMapper;
    private final AdminLoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final SysConfigReader sysConfigReader;
    private final AdminPermissionAssembler permissionAssembler;
    private final RememberMeService rememberMeService;

    /** 登录（含失败锁定策略、踢下线签发与记住我长期 token） */
    @Transactional(rollbackFor = Exception.class)
    public LoginResultVO login(LoginRequest request, HttpServletResponse response) {
        // [1] 验证码：一次性消费；错误直接返回 11001，不增加账号失败次数（T-0013 验收）
        captchaService.assertValid(request.getCaptchaId(), request.getCaptchaAnswer());

        // [2] 查账号
        AdminUser user = adminUserMapper.selectOne(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getUsername, request.getUsername()));

        if (user == null) {
            writeLoginLog(request.getUsername(), null, "FAIL", "BAD_CREDENTIALS");
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }

        // [3] 停用检查
        if (user.getStatus() == null || user.getStatus() != 1) {
            writeLoginLog(user.getUsername(), user.getId(), "FAIL", "ACCOUNT_DISABLED");
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }

        // [4] 锁定检查
        if (user.getLockExpireTime() != null && user.getLockExpireTime().isAfter(LocalDateTime.now())) {
            writeLoginLog(user.getUsername(), user.getId(), "LOCKED", "ACCOUNT_LOCKED");
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        // [5] 密码校验（BCrypt）
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleLoginFailure(user);
            throw new BusinessException(ErrorCode.BAD_CREDENTIALS);
        }

        // [6] 成功：清零失败计数与锁定、更新最后登录时间
        //     （lock_expire_time 需显式 set null，updateById 会忽略 null 字段）
        adminUserMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<AdminUser>lambdaUpdate()
                .eq(AdminUser::getId, user.getId())
                .set(AdminUser::getLoginFailCount, 0)
                .set(AdminUser::getLockExpireTime, null)
                .set(AdminUser::getLastLoginTime, LocalDateTime.now()));

        // [7] Sa-Token 签发。sa-token.is-concurrent=false：同账号旧 token 被顶替为
        //     BE_REPLACED 状态（后登踢先登），旧端下次访问得到 20004"被踢下线"语义，
        //     与普通过期（20001 未登录）互不混淆。不使用提前 logout，避免语义降级。
        StpUtil.login(user.getId());

        // [8] 组装权限快照写入 Session（供 StpInterface / 数据权限拦截器读取）
        boolean firstLogin = user.getFirstLogin() != null && user.getFirstLogin() == 1;
        AdminLoginUser loginUser = permissionAssembler.assemble(
                user.getId(), user.getUsername(), user.getNickname(), firstLogin);
        StpUtil.getSession().set(AdminLoginUser.SESSION_KEY, loginUser);

        writeLoginLog(user.getUsername(), user.getId(), "SUCCESS", null);

        // [9] 记住我（T-0034）：仅勾选时签发长期 token 至 HttpOnly Cookie；未勾选不签发
        if (request.isRememberMe()) {
            rememberMeService.issue(user.getId(), response);
        } else {
            // 未勾选时清除可能残留的旧长期 Cookie，避免"上次记住我"泄漏到本次会话
            rememberMeService.revoke(user.getId());
            rememberMeService.clearCookie(response);
        }

        LoginResultVO vo = new LoginResultVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setTokenTimeout(StpUtil.getTokenTimeout());
        vo.setFirstLogin(firstLogin);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        return vo;
    }

    /**
     * 自动续登（T-0034，前端启动时调用）：校验 HttpOnly Cookie 中的长期 token，
     * 有效则重建 Sa-Token 短期会话并滚动刷新长期 token；无效/过期返回 null。
     */
    @Transactional(rollbackFor = Exception.class)
    public LoginResultVO autoLogin(HttpServletRequest request, HttpServletResponse response) {
        Long userId = rememberMeService.verify(request);
        if (userId == null) {
            return null;
        }
        AdminUser user = adminUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            // 账号已删除/停用：长期 token 立即失效
            rememberMeService.revoke(userId);
            rememberMeService.clearCookie(response);
            return null;
        }
        StpUtil.login(userId);
        boolean firstLogin = user.getFirstLogin() != null && user.getFirstLogin() == 1;
        AdminLoginUser loginUser = permissionAssembler.assemble(
                userId, user.getUsername(), user.getNickname(), firstLogin);
        StpUtil.getSession().set(AdminLoginUser.SESSION_KEY, loginUser);
        rememberMeService.refresh(userId, response); // 滚动续期（新 token 换旧 token）
        writeLoginLog(user.getUsername(), userId, "SUCCESS", "REMEMBER_ME");

        LoginResultVO vo = new LoginResultVO();
        vo.setToken(StpUtil.getTokenValue());
        vo.setTokenTimeout(StpUtil.getTokenTimeout());
        vo.setFirstLogin(firstLogin);
        vo.setUserId(userId);
        vo.setUsername(user.getUsername());
        return vo;
    }

    /** 注销：清除登录态、账号在线映射与 Remember-Me 长期 token（T-0034：主动退出后长 token 立即失效） */
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        Long userId = StpUtil.getLoginIdAsLong();
        StpUtil.logout(userId);
        rememberMeService.revoke(userId);
        rememberMeService.clearCookie(response);
    }

    /** 当前登录用户信息（用户+角色+权限码集合） */
    public WhoamiVO whoami() {
        AdminLoginUser user = requireFreshSnapshot();
        return WhoamiVO.from(user);
    }

    /**
     * 修改自身密码（T-0017）：校验旧密码；成功后作废当前账号所有会话（含本会话）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(ChangePasswordRequest request) {
        AdminLoginUser current = requireFreshSnapshot();
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCH_CONFIRM);
        }
        AdminUser user = adminUserMapper.selectById(current.getUserId());
        if (user == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }

        AdminUser update = new AdminUser();
        update.setId(user.getId());
        update.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        update.setFirstLogin(0); // 完成改密即解除首次登录标记
        adminUserMapper.updateById(update);

        // 修改后所有未失效旧会话立即作废（FR-AUTH-05 验收），当前端需重新登录；
        // Remember-Me 长期 token 同步失效（T-0034：凭证变更后不得自动续登）
        StpUtil.logout(user.getId());
        rememberMeService.revoke(user.getId());
    }

    // ===================== internal =====================

    /** 读取会话快照；若缺失（如角色变更后）按当前库内数据重建 */
    private AdminLoginUser requireFreshSnapshot() {
        Object obj = StpUtil.getSession().get(AdminLoginUser.SESSION_KEY);
        if (obj instanceof AdminLoginUser u) {
            return u;
        }
        AdminUser user = adminUserMapper.selectById(StpUtil.getLoginIdAsLong());
        if (user == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND);
        }
        AdminLoginUser fresh = permissionAssembler.assemble(user.getId(), user.getUsername(),
                user.getNickname(), user.getFirstLogin() != null && user.getFirstLogin() == 1);
        StpUtil.getSession().set(AdminLoginUser.SESSION_KEY, fresh);
        return fresh;
    }

    /** 登录失败：递增计数，达到阈值设置锁定到期时间并清零计数 */
    private void handleLoginFailure(AdminUser user) {
        int threshold = sysConfigReader.loginFailThreshold();
        int lockMinutes = sysConfigReader.loginLockMinutes();
        int failCount = (user.getLoginFailCount() == null ? 0 : user.getLoginFailCount()) + 1;

        AdminUser update = new AdminUser();
        update.setId(user.getId());
        if (failCount >= threshold) {
            update.setLoginFailCount(0);
            update.setLockExpireTime(LocalDateTime.now().plusMinutes(lockMinutes));
            writeLoginLog(user.getUsername(), user.getId(), "LOCKED", "ACCOUNT_LOCKED");
        } else {
            update.setLoginFailCount(failCount);
            writeLoginLog(user.getUsername(), user.getId(), "FAIL", "BAD_CREDENTIALS");
        }
        adminUserMapper.updateById(update);
    }

    private void writeLoginLog(String username, Long userId, String result, String failReason) {
        try {
            AdminLoginLog logEntry = new AdminLoginLog();
            logEntry.setUsername(username);
            logEntry.setUserId(userId);
            logEntry.setLoginType("PASSWORD");
            logEntry.setResult(result);
            logEntry.setFailReason(failReason);
            logEntry.setLoginTime(LocalDateTime.now());
            HttpServletRequest req = currentRequest();
            if (req != null) {
                logEntry.setIp(clientIp(req));
                String ua = req.getHeader("User-Agent");
                logEntry.setUserAgent(ua != null && ua.length() > 512 ? ua.substring(0, 512) : ua);
            }
            loginLogMapper.insert(logEntry);
        } catch (Exception e) {
            // 登录日志失败不阻断主流程
            log.error("写入登录日志失败: {}", e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private String clientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        return request.getRemoteAddr();
    }
}
