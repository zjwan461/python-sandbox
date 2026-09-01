package io.github.sandbox.admin.rbac.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.security.DataScopes;
import io.github.sandbox.admin.common.security.SecurityUtils;
import io.github.sandbox.admin.rbac.dto.UserQuery;
import io.github.sandbox.admin.rbac.dto.UserUpsertRequest;
import io.github.sandbox.admin.rbac.dto.UserVO;
import io.github.sandbox.admin.rbac.entity.AdminRole;
import io.github.sandbox.admin.rbac.entity.AdminUser;
import io.github.sandbox.admin.rbac.entity.AdminUserRole;
import io.github.sandbox.admin.rbac.mapper.AdminRoleMapper;
import io.github.sandbox.admin.rbac.mapper.AdminUserMapper;
import io.github.sandbox.admin.rbac.mapper.AdminUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户管理服务（T-0018 后端部分）：分页筛选、新增、编辑、启停用、
 * 重置密码、分配角色、手动解锁。
 *
 * <p>本批次不含删除用户（历史数据归属转移的完整口径随批次3 的 ApiKey/客户端
 * 引用校验任务完善）；启停用即业务侧的"下线"手段。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final PasswordEncoder passwordEncoder;

    /** 分页 + 筛选 */
    public PageResult<UserVO> page(UserQuery query) {
        LambdaQueryWrapper<AdminUser> wrapper = Wrappers.<AdminUser>lambdaQuery()
                .like(StringUtils.hasText(query.getUsername()), AdminUser::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getNickname()), AdminUser::getNickname, query.getNickname())
                .eq(query.getStatus() != null, AdminUser::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getDeptName()), AdminUser::getDeptName, query.getDeptName())
                .orderByDesc(AdminUser::getId);
        Page<AdminUser> page = adminUserMapper.selectPage(
                new Page<>(Math.max(1, query.getPageNum()), clampSize(query.getPageSize())), wrapper);

        List<UserVO> vos = page.getRecords().stream().map(this::toVO).toList();
        attachRoles(vos);
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 详情 */
    public UserVO detail(Long id) {
        AdminUser user = requireUser(id);
        UserVO vo = toVO(user);
        attachRoles(List.of(vo));
        return vo;
    }

    /** 新增用户（默认首次登录强制改密） */
    @Transactional(rollbackFor = Exception.class)
    public Long create(UserUpsertRequest request) {
        if (!StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新增用户必须提供初始密码");
        }
        if (existsUsername(request.getUsername(), null)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        AdminUser user = new AdminUser();
        user.setUsername(request.getUsername());
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setEmail(request.getEmail());
        user.setPhone(request.getPhone());
        user.setAvatar(request.getAvatar());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        user.setDeptName(request.getDeptName());
        user.setRemark(request.getRemark());
        user.setLoginFailCount(0);
        user.setFirstLogin(1); // 管理员设置的初始密码 → 首次登录强制改密
        adminUserMapper.insert(user);
        bindRoles(user.getId(), request.getRoleIds(), true);
        return user.getId();
    }

    /** 编辑用户（不含密码；密码走重置通道） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, UserUpsertRequest request) {
        AdminUser existing = requireUser(id);
        if (existsUsername(request.getUsername(), id)) {
            throw new BusinessException(ErrorCode.USERNAME_EXISTS);
        }
        AdminUser update = new AdminUser();
        update.setId(id);
        update.setUsername(request.getUsername());
        update.setNickname(request.getNickname());
        update.setEmail(request.getEmail());
        update.setPhone(request.getPhone());
        update.setAvatar(request.getAvatar());
        update.setDeptName(request.getDeptName());
        update.setRemark(request.getRemark());
        // 不允许通过编辑接口改状态（启停独立接口，保证审计语义）
        adminUserMapper.updateById(update);
        if (request.getRoleIds() != null) {
            bindRoles(id, request.getRoleIds(), true);
        }
    }

    /** 启停用（停用即时踢下线；不允许停用当前登录账号或最后一个超管） */
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "状态仅支持 0=停用 1=启用");
        }
        AdminUser user = requireUser(id);
        Long currentUserId = SecurityUtils.getUserId();
        if (status == 0) {
            if (id.equals(currentUserId)) {
                throw new BusinessException(ErrorCode.NO_SELF_DISABLE);
            }
            if (isLastSuperAdmin(user)) {
                throw new BusinessException(ErrorCode.LAST_SUPERADMIN_PROTECTED);
            }
        }
        AdminUser update = new AdminUser();
        update.setId(id);
        update.setStatus(status);
        adminUserMapper.updateById(update);
        if (status == 0) {
            // 停用即刻使该账号在线会话失效（python-sandbox 侧拒绝语义由批次3 ApiKey 校验联动）
            StpUtil.logout(id);
        }
    }

    /** 管理员重置密码（重置后置首次登录标记并作废全部会话） */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        if (!StringUtils.hasText(newPassword) || newPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新密码长度不得少于8位");
        }
        requireUser(id);
        // lock_expire_time 显式置空（updateById 忽略 null 字段）
        adminUserMapper.update(null, Wrappers.<AdminUser>lambdaUpdate()
                .eq(AdminUser::getId, id)
                .set(AdminUser::getPasswordHash, passwordEncoder.encode(newPassword))
                .set(AdminUser::getFirstLogin, 1)
                .set(AdminUser::getLoginFailCount, 0)
                .set(AdminUser::getLockExpireTime, null));
        StpUtil.logout(id); // 重置后旧会话作废
    }

    /** 手动解锁（FR-AUTH-05：清除锁定到期时间与失败计数） */
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long id) {
        requireUser(id);
        adminUserMapper.update(null, Wrappers.<AdminUser>lambdaUpdate()
                .eq(AdminUser::getId, id)
                .set(AdminUser::getLoginFailCount, 0)
                .set(AdminUser::getLockExpireTime, null));
    }

    /** 分配角色 */
    @Transactional(rollbackFor = Exception.class)
    public void assignRoles(Long id, List<Long> roleIds) {
        requireUser(id);
        bindRoles(id, roleIds, true);
        StpUtil.logout(id); // 角色变更即刻作废会话，重新登录后获得新权限快照
    }

    // ===================== internal =====================

    private AdminUser requireUser(Long id) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private boolean existsUsername(String username, Long excludeId) {
        return adminUserMapper.selectCount(Wrappers.<AdminUser>lambdaQuery()
                .eq(AdminUser::getUsername, username)
                .ne(excludeId != null, AdminUser::getId, excludeId)) > 0;
    }

    /** 是否最后一个启用态超级管理员（保护口径） */
    private boolean isLastSuperAdmin(AdminUser user) {
        List<String> roleKeys = adminRoleMapper.selectRolesByUserId(user.getId()).stream()
                .map(AdminRole::getRoleKey).toList();
        if (!roleKeys.contains(DataScopes.ROLE_SUPERADMIN)) {
            return false;
        }
        // 找出所有超管用户
        AdminRole superRole = adminRoleMapper.selectOne(Wrappers.<AdminRole>lambdaQuery()
                .eq(AdminRole::getRoleKey, DataScopes.ROLE_SUPERADMIN).last("LIMIT 1"));
        if (superRole == null) {
            return false;
        }
        List<Long> superUserIds = adminUserRoleMapper.selectList(Wrappers.<AdminUserRole>lambdaQuery()
                        .eq(AdminUserRole::getRoleId, superRole.getId()))
                .stream().map(AdminUserRole::getUserId).toList();
        if (superUserIds.isEmpty()) {
            return false;
        }
        long activeOthers = adminUserMapper.selectCount(Wrappers.<AdminUser>lambdaQuery()
                .in(AdminUser::getId, superUserIds)
                .eq(AdminUser::getStatus, 1)
                .ne(AdminUser::getId, user.getId()));
        return activeOthers == 0;
    }

    private void bindRoles(Long userId, List<Long> roleIds, boolean replace) {
        if (roleIds == null) {
            return;
        }
        if (roleIds.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户至少需要关联一个角色");
        }
        long valid = adminRoleMapper.selectCount(Wrappers.<AdminRole>lambdaQuery()
                .in(AdminRole::getId, roleIds));
        if (valid != roleIds.stream().distinct().count()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "存在无效角色ID");
        }
        if (replace) {
            adminUserRoleMapper.delete(Wrappers.<AdminUserRole>lambdaQuery()
                    .eq(AdminUserRole::getUserId, userId));
        }
        for (Long roleId : roleIds.stream().distinct().toList()) {
            AdminUserRole ur = new AdminUserRole();
            ur.setUserId(userId);
            ur.setRoleId(roleId);
            adminUserRoleMapper.insert(ur);
        }
    }

    private UserVO toVO(AdminUser u) {
        UserVO vo = new UserVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setEmail(u.getEmail());
        vo.setPhone(u.getPhone());
        vo.setAvatar(u.getAvatar());
        vo.setStatus(u.getStatus());
        vo.setDeptName(u.getDeptName());
        vo.setLockExpireTime(u.getLockExpireTime());
        vo.setLocked(u.getLockExpireTime() != null && u.getLockExpireTime().isAfter(LocalDateTime.now()));
        vo.setFirstLogin(u.getFirstLogin());
        vo.setLastLoginTime(u.getLastLoginTime());
        vo.setRemark(u.getRemark());
        vo.setCreateTime(u.getCreateTime());
        vo.setCreateBy(u.getCreateBy());
        vo.setUpdateTime(u.getUpdateTime());
        vo.setUpdateBy(u.getUpdateBy());
        return vo;
    }

    private void attachRoles(List<UserVO> vos) {
        if (vos.isEmpty()) {
            return;
        }
        List<Long> userIds = vos.stream().map(UserVO::getId).toList();
        List<AdminUserRole> relations = adminUserRoleMapper.selectList(Wrappers.<AdminUserRole>lambdaQuery()
                .in(AdminUserRole::getUserId, userIds));
        Map<Long, AdminRole> roleMap = relations.isEmpty() ? Map.of()
                : adminRoleMapper.selectBatchIds(relations.stream().map(AdminUserRole::getRoleId).distinct().toList())
                .stream().collect(Collectors.toMap(AdminRole::getId, r -> r));
        Map<Long, List<UserVO.RoleBrief>> byUser = new HashMap<>();
        for (AdminUserRole rel : relations) {
            AdminRole role = roleMap.get(rel.getRoleId());
            if (role == null) {
                continue;
            }
            UserVO.RoleBrief brief = new UserVO.RoleBrief();
            brief.setId(role.getId());
            brief.setRoleName(role.getRoleName());
            brief.setRoleKey(role.getRoleKey());
            byUser.computeIfAbsent(rel.getUserId(), k -> new java.util.ArrayList<>()).add(brief);
        }
        vos.forEach(vo -> vo.setRoles(byUser.getOrDefault(vo.getId(), List.of())));
    }

    private long clampSize(long size) {
        return Math.min(Math.max(1, size), 200);
    }
}
