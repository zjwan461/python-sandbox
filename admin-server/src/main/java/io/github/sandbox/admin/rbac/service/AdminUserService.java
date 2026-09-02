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
import io.github.sandbox.admin.rbac.dto.UserImportResultVO;
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
 * 用户管理服务（T-0018）：分页筛选、新增、编辑、启停用、重置密码、
 * 分配角色、手动解锁、单删与批量删除（批次6 补齐软删除+归属转移）。
 *
 * <p>删除语义（FR-USER-03、requirements.md §10.3.8、design.md §5.5、默认决策 #8）：</p>
 * <ul>
 *   <li>已登录（存在 Sa-Token 在线会话）或仍持有"未撤销且未过期"绑定 ApiKey 的用户禁止删除（12006）。</li>
 *   <li>可删除用户执行软删除（deleted=1 + 停用 + 作废旧会话与 Remember-Me token）；
 *       其历史数据归属转移至执行删除的管理员（client_app.owner_user_id、
 *       client_api_key.bound_user_id 改写为操作人），确保历史日志归属不悬空，
 *       且仅管理员/审计员可见（软删除后 SELF 行过滤不再命中该用户，ALL 域仍可见）。</li>
 *   <li>最后一个超级管理员不可删除（12007）；不允许删除当前登录账号自身（12008）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final AdminRoleMapper adminRoleMapper;
    private final AdminUserRoleMapper adminUserRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final io.github.sandbox.admin.client.mapper.ClientAppMapper clientAppMapper;
    private final io.github.sandbox.admin.apikey.mapper.ClientApiKeyMapper clientApiKeyMapper;
    private final io.github.sandbox.admin.auth.service.RememberMeService rememberMeService;

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
            rememberMeService.revoke(id); // 停用即刻使 Remember-Me 长期 token 失效（T-0034）
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
        rememberMeService.revoke(id); // 重置后长期免登 token 一并作废（T-0034 凭证变更口径）
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

    /**
     * 删除用户（T-0018 批次6 补齐；FR-USER-03）：单删委托批删，保证口径一致。
     *
     * @return 实际删除的用户名列表（用于审计 targetName）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<String> delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择要删除的用户");
        }
        Long operatorId = SecurityUtils.getUserId();
        List<String> deletedNames = new java.util.ArrayList<>();
        for (Long id : ids.stream().distinct().toList()) {
            AdminUser user = requireUser(id);
            // 业务规则阻止（FR-USER-03、requirements.md §10.3.8）
            if (id.equals(operatorId)) {
                throw new BusinessException(ErrorCode.NO_SELF_DISABLE, "不允许删除当前登录账号");
            }
            if (isLastSuperAdmin(user)) {
                throw new BusinessException(ErrorCode.LAST_SUPERADMIN_PROTECTED, "不允许删除最后一个超级管理员");
            }
            if (!StpUtil.getTokenValueListByLoginId(id).isEmpty()) {
                throw new BusinessException(ErrorCode.USER_HAS_ACTIVE_RESOURCE,
                        "用户 " + user.getUsername() + " 当前已登录，禁止删除（请先停用）");
            }
            long activeKeys = io.github.sandbox.admin.common.datapermission.DataPermissionIgnoreHolder.runIgnored(
                    () -> clientApiKeyMapper.selectCount(Wrappers.<io.github.sandbox.admin.apikey.entity.ClientApiKey>lambdaQuery()
                            .eq(io.github.sandbox.admin.apikey.entity.ClientApiKey::getBoundUserId, id)
                            .in(io.github.sandbox.admin.apikey.entity.ClientApiKey::getStatus,
                                    io.github.sandbox.admin.apikey.entity.ClientApiKey.STATUS_ENABLED,
                                    io.github.sandbox.admin.apikey.entity.ClientApiKey.STATUS_DISABLED)));
            if (activeKeys > 0) {
                throw new BusinessException(ErrorCode.USER_HAS_ACTIVE_RESOURCE,
                        "用户 " + user.getUsername() + " 仍持有 " + activeKeys + " 个有效绑定 ApiKey，禁止删除（请先撤销）");
            }
            // 软删除 + 停用（双保险：即便绕过逻辑删除查询也不可见）
            adminUserMapper.update(null, Wrappers.<AdminUser>lambdaUpdate()
                    .eq(AdminUser::getId, id)
                    .set(AdminUser::getStatus, 0));
            adminUserMapper.deleteById(id);
            // 历史归属转移（design.md §5.5：不悬空；转移至执行删除的管理员，仅 ALL 域可见其后续归属数据）
            io.github.sandbox.admin.common.datapermission.DataPermissionIgnoreHolder.runIgnored(() -> {
                clientAppMapper.update(null, Wrappers.<io.github.sandbox.admin.client.entity.ClientApp>lambdaUpdate()
                        .eq(io.github.sandbox.admin.client.entity.ClientApp::getOwnerUserId, id)
                        .set(io.github.sandbox.admin.client.entity.ClientApp::getOwnerUserId, operatorId));
                clientApiKeyMapper.update(null, Wrappers.<io.github.sandbox.admin.apikey.entity.ClientApiKey>lambdaUpdate()
                        .eq(io.github.sandbox.admin.apikey.entity.ClientApiKey::getBoundUserId, id)
                        .set(io.github.sandbox.admin.apikey.entity.ClientApiKey::getBoundUserId, operatorId));
            });
            // 会话与长期免登 token 即刻作废
            try {
                StpUtil.logout(id);
            } catch (Exception ignored) {
                // 个别登出异常不阻断删除
            }
            rememberMeService.revoke(id);
            deletedNames.add(user.getUsername());
        }
        return deletedNames;
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

    // ===================== T-0043 导入/导出 =====================

    /** 用户导出列（不含密码/摘要/内部凭证，验收口径） */
    private static final List<String> USER_EXPORT_HEADERS =
            List.of("用户名", "昵称", "邮箱", "手机", "部门", "状态", "角色", "最后登录时间", "创建时间");

    /**
     * 用户 CSV 导出（T-0043，FR-USER-07）：范围为当前查询筛选结果（数据权限同 page），
     * 不含密码、ApiKey、内部凭证。
     */
    public byte[] exportUsers(UserQuery query) {
        LambdaQueryWrapper<AdminUser> wrapper = Wrappers.<AdminUser>lambdaQuery()
                .like(StringUtils.hasText(query.getUsername()), AdminUser::getUsername, query.getUsername())
                .like(StringUtils.hasText(query.getNickname()), AdminUser::getNickname, query.getNickname())
                .eq(query.getStatus() != null, AdminUser::getStatus, query.getStatus())
                .like(StringUtils.hasText(query.getDeptName()), AdminUser::getDeptName, query.getDeptName())
                .orderByDesc(AdminUser::getId)
                .last("LIMIT 10000");
        List<AdminUser> users = adminUserMapper.selectList(wrapper);
        List<UserVO> vos = users.stream().map(this::toVO).toList();
        attachRoles(vos);
        List<List<Object>> rows = vos.stream().map(v -> List.<Object>of(
                nzv(v.getUsername()), nzv(v.getNickname()), nzv(v.getEmail()), nzv(v.getPhone()),
                nzv(v.getDeptName()), v.getStatus() != null && v.getStatus() == 1 ? "启用" : "停用",
                v.getRoles() == null ? "" : v.getRoles().stream()
                        .map(UserVO.RoleBrief::getRoleKey).collect(java.util.stream.Collectors.joining(";")),
                v.getLastLoginTime() == null ? "" : v.getLastLoginTime().toString(),
                v.getCreateTime() == null ? "" : v.getCreateTime().toString())).toList();
        return io.github.sandbox.admin.common.util.ExportUtil.toCsv(USER_EXPORT_HEADERS, rows);
    }

    /**
     * 用户 CSV 批量导入（T-0043，FR-USER-07）。
     *
     * <p>CSV 列（首行表头）：username,nickname,email,phone,deptName,roleKeys(分号分隔)。</p>
     * <ul>
     *   <li>重复用户名、非法字段、缺失必填项逐行拒绝并给出原因，不静默覆盖（验收）。</li>
     *   <li>导入用户统一下发调用方提供的初始密码（≥8位），firstLogin=1 强制首登改密；
     *       导出内容不含任何密码字段。</li>
     *   <li>roleKeys 必须全部命中现有角色，否则该行失败（用户至少一个角色）。</li>
     * </ul>
     */
    @Transactional(rollbackFor = Exception.class)
    public UserImportResultVO importUsers(byte[] csvBytes, String initialPassword) {
        if (!StringUtils.hasText(initialPassword) || initialPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "必须提供不少于8位的初始密码");
        }
        UserImportResultVO result = new UserImportResultVO();
        List<List<String>> rows = parseCsv(new String(csvBytes, java.nio.charset.StandardCharsets.UTF_8));
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "CSV 内容为空");
        }
        // 表头识别（不区分大小写）：首行含 username 视为表头跳过
        int start = 0;
        List<String> header = rows.get(0);
        if (!header.isEmpty() && "username".equalsIgnoreCase(header.get(0).trim())) {
            start = 1;
        }
        List<AdminRole> allRoles = adminRoleMapper.selectList(Wrappers.<AdminRole>lambdaQuery());
        Map<String, AdminRole> roleByKey = allRoles.stream()
                .collect(java.util.stream.Collectors.toMap(AdminRole::getRoleKey, r -> r, (a, b) -> a));
        int rowNum = 0;
        for (int i = start; i < rows.size(); i++) {
            List<String> cols = rows.get(i);
            rowNum = i - start + 1;
            if (cols.stream().allMatch(c -> c == null || c.isBlank())) {
                continue; // 跳过空行
            }
            String username = col(cols, 0);
            if (username == null || username.isBlank()) {
                result.addFailure(rowNum, "用户名不能为空");
                continue;
            }
            if (username.length() > 64) {
                result.addFailure(rowNum, "用户名长度超过64");
                continue;
            }
            if (existsUsername(username, null)) {
                result.addFailure(rowNum, "用户名已存在（不覆盖既有账号）");
                continue;
            }
            String email = col(cols, 2);
            if (email != null && !email.isBlank() && !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                result.addFailure(rowNum, "邮箱格式非法: " + email);
                continue;
            }
            String roleKeysRaw = col(cols, 5);
            List<Long> roleIds = new java.util.ArrayList<>();
            if (roleKeysRaw == null || roleKeysRaw.isBlank()) {
                result.addFailure(rowNum, "角色(roleKeys)不能为空，用户至少需要一个角色");
                continue;
            }
            boolean rolesValid = true;
            for (String rk : roleKeysRaw.split(";")) {
                if (rk.isBlank()) {
                    continue;
                }
                AdminRole role = roleByKey.get(rk.trim());
                if (role == null) {
                    result.addFailure(rowNum, "未知角色权限字符: " + rk.trim());
                    rolesValid = false;
                    break;
                }
                roleIds.add(role.getId());
            }
            if (!rolesValid || roleIds.isEmpty()) {
                continue;
            }
            UserUpsertRequest req = new UserUpsertRequest();
            req.setUsername(username);
            req.setNickname(blankToNull(col(cols, 1)));
            req.setEmail(blankToNull(email));
            req.setPhone(blankToNull(col(cols, 3)));
            req.setDeptName(blankToNull(col(cols, 4)));
            req.setPassword(initialPassword);
            req.setStatus(1);
            req.setRoleIds(roleIds);
            req.setRemark("CSV 批量导入");
            try {
                create(req);
                result.getSuccessUsernames().add(username);
            } catch (BusinessException e) {
                result.addFailure(rowNum, e.getMessage());
            }
        }
        result.setTotal(rowNum);
        return result;
    }

    // ===================== csv helpers =====================

    private String nzv(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private String col(List<String> cols, int idx) {
        return idx < cols.size() ? cols.get(idx).trim() : null;
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** 极简 RFC4180 CSV 解析（支持引号包裹字段、内嵌逗号/双引号/换行） */
    private List<List<String>> parseCsv(String text) {
        List<List<String>> rows = new java.util.ArrayList<>();
        List<String> current = new java.util.ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(ch);
                }
                continue;
            }
            switch (ch) {
                case '"' -> inQuotes = true;
                case ',' -> {
                    current.add(cell.toString());
                    cell.setLength(0);
                }
                case '\r' -> {
                    // ignore, handle with \n
                }
                case '\n' -> {
                    current.add(cell.toString());
                    cell.setLength(0);
                    rows.add(current);
                    current = new java.util.ArrayList<>();
                }
                default -> cell.append(ch);
            }
        }
        if (cell.length() > 0 || !current.isEmpty()) {
            current.add(cell.toString());
            rows.add(current);
        }
        return rows;
    }
}
