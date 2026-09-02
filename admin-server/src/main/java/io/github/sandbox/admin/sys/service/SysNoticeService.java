package io.github.sandbox.admin.sys.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.sandbox.admin.common.exception.BusinessException;
import io.github.sandbox.admin.common.exception.ErrorCode;
import io.github.sandbox.admin.common.result.PageResult;
import io.github.sandbox.admin.common.security.AdminLoginUser;
import io.github.sandbox.admin.common.security.SecurityUtils;
import io.github.sandbox.admin.sys.dto.SysNoticeUpsertRequest;
import io.github.sandbox.admin.sys.dto.SysNoticeVO;
import io.github.sandbox.admin.sys.entity.SysNotice;
import io.github.sandbox.admin.sys.entity.SysNoticeRead;
import io.github.sandbox.admin.sys.mapper.SysNoticeMapper;
import io.github.sandbox.admin.sys.mapper.SysNoticeReadMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 通知公告业务（T-0042，FR-SYS-02~03；design.md §10.3）。
 *
 * <p>角色口径（验收）：</p>
 * <ul>
 *   <li>管理动作（新增/编辑/删除/发布/下线）由 Controller 的 notice:add/edit/delete
 *       权限码约束（种子授权=超管/管理员），普通用户不能管理公告。</li>
 *   <li>投递侧（unread/list、mark-read）仅要求登录：任何角色都可读取"处于有效窗口内
 *       且已发布"的公告；未到生效时间或超过失效时间的公告不展示（验收）。</li>
 *   <li>公告不落 SELF 数据权限注册表（sys_notice 为全局共享内容，非归属数据）。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SysNoticeService {

    private final SysNoticeMapper noticeMapper;
    private final SysNoticeReadMapper noticeReadMapper;

    // ===================== 管理侧 =====================

    /** 管理列表（全部状态，可按标题/状态筛选，置顶+发布时间倒序） */
    public PageResult<SysNoticeVO> pageForAdmin(String title, Integer status, long pageNum, long pageSize) {
        LambdaQueryWrapper<SysNotice> wrapper = Wrappers.<SysNotice>lambdaQuery()
                .like(StringUtils.hasText(title), SysNotice::getTitle, title)
                .eq(status != null, SysNotice::getStatus, status)
                .orderByDesc(SysNotice::getIsTop)
                .orderByDesc(SysNotice::getPublishTime)
                .orderByDesc(SysNotice::getId);
        Page<SysNotice> page = noticeMapper.selectPage(
                new Page<>(Math.max(1, pageNum), Math.min(Math.max(1, pageSize), 200)), wrapper);
        Set<Long> readIds = currentUserReadIds();
        List<SysNoticeVO> vos = page.getRecords().stream().map(n -> toVO(n, readIds)).toList();
        return new PageResult<>(vos, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 新增（保存为草稿，发布走独立动作，保证审计与投递语义分离） */
    @Transactional(rollbackFor = Exception.class)
    public Long create(SysNoticeUpsertRequest request) {
        validateWindow(request);
        SysNotice notice = new SysNotice();
        notice.setTitle(request.getTitle());
        notice.setContent(request.getContent());
        notice.setEffectiveTime(request.getEffectiveTime());
        notice.setExpireTime(request.getExpireTime());
        notice.setIsTop(request.getIsTop() == null || request.getIsTop() != 1 ? 0 : 1);
        notice.setStatus(SysNotice.STATUS_DRAFT);
        noticeMapper.insert(notice);
        return notice.getId();
    }

    /** 编辑（不改变发布状态；下线后重新发布前编辑不影响投递——草稿/已下线均可编辑） */
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SysNoticeUpsertRequest request) {
        requireNotice(id);
        validateWindow(request);
        SysNotice update = new SysNotice();
        update.setId(id);
        update.setTitle(request.getTitle());
        update.setContent(request.getContent());
        update.setEffectiveTime(request.getEffectiveTime());
        update.setExpireTime(request.getExpireTime());
        update.setIsTop(request.getIsTop() == null || request.getIsTop() != 1 ? 0 : 1);
        noticeMapper.updateById(update);
    }

    /** 删除（逻辑删除；已读记录保留不影响——公告不可见即不投递） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireNotice(id);
        noticeMapper.deleteById(id);
    }

    /** 发布：草稿/已下线 -> 已发布，回填发布人与发布时间 */
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        SysNotice notice = requireNotice(id);
        AdminLoginUser me = SecurityUtils.getLoginUser();
        SysNotice update = new SysNotice();
        update.setId(notice.getId());
        update.setStatus(SysNotice.STATUS_PUBLISHED);
        update.setPublisherId(me.getUserId());
        update.setPublisherName(me.getUsername());
        update.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(update);
    }

    /** 下线：已发布 -> 草稿（不再投递） */
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(Long id) {
        requireNotice(id);
        SysNotice update = new SysNotice();
        update.setId(id);
        update.setStatus(SysNotice.STATUS_DRAFT);
        noticeMapper.updateById(update);
    }

    // ===================== 投递侧（登录用户） =====================

    /** 当前用户可见公告（已发布 + 处于有效窗口；置顶优先、发布时间倒序），含已读标记 */
    public List<SysNoticeVO> visibleForCurrentUser() {
        LocalDateTime now = LocalDateTime.now();
        List<SysNotice> list = noticeMapper.selectList(Wrappers.<SysNotice>lambdaQuery()
                .eq(SysNotice::getStatus, SysNotice.STATUS_PUBLISHED)
                .and(w -> w.isNull(SysNotice::getEffectiveTime).or().le(SysNotice::getEffectiveTime, now))
                .and(w -> w.isNull(SysNotice::getExpireTime).or().gt(SysNotice::getExpireTime, now))
                .orderByDesc(SysNotice::getIsTop)
                .orderByDesc(SysNotice::getPublishTime));
        Set<Long> readIds = currentUserReadIds();
        return list.stream().map(n -> toVO(n, readIds)).toList();
    }

    /** 当前用户未读数量（登录后通栏徽标用） */
    public long unreadCount() {
        return visibleForCurrentUser().stream().filter(v -> !v.isRead()).count();
    }

    /** 标记已读（幂等：重复标记忽略） */
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long noticeId) {
        requireNotice(noticeId);
        AdminLoginUser me = SecurityUtils.getLoginUser();
        long exists = noticeReadMapper.selectCount(Wrappers.<SysNoticeRead>lambdaQuery()
                .eq(SysNoticeRead::getNoticeId, noticeId)
                .eq(SysNoticeRead::getUserId, me.getUserId()));
        if (exists > 0) {
            return;
        }
        SysNoticeRead read = new SysNoticeRead();
        read.setNoticeId(noticeId);
        read.setUserId(me.getUserId());
        read.setReadTime(LocalDateTime.now());
        try {
            noticeReadMapper.insert(read);
        } catch (Exception e) {
            // 联合唯一冲突（并发重复标记）按幂等处理
            if (e.getMessage() == null || !e.getMessage().contains("Duplicate")) {
                throw e;
            }
        }
    }

    // ===================== internal =====================

    private SysNotice requireNotice(Long id) {
        SysNotice notice = noticeMapper.selectById(id);
        if (notice == null) {
            throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "公告不存在");
        }
        return notice;
    }

    private void validateWindow(SysNoticeUpsertRequest request) {
        if (request.getEffectiveTime() != null && request.getExpireTime() != null
                && !request.getEffectiveTime().isBefore(request.getExpireTime())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "生效时间必须早于失效时间");
        }
    }

    private Set<Long> currentUserReadIds() {
        AdminLoginUser me = SecurityUtils.getLoginUserQuietly();
        if (me == null) {
            return Set.of();
        }
        return noticeReadMapper.selectList(Wrappers.<SysNoticeRead>lambdaQuery()
                        .eq(SysNoticeRead::getUserId, me.getUserId()))
                .stream().map(SysNoticeRead::getNoticeId).collect(Collectors.toSet());
    }

    private SysNoticeVO toVO(SysNotice n, Set<Long> readIds) {
        SysNoticeVO vo = new SysNoticeVO();
        vo.setId(n.getId());
        vo.setTitle(n.getTitle());
        vo.setContent(n.getContent());
        vo.setEffectiveTime(n.getEffectiveTime());
        vo.setExpireTime(n.getExpireTime());
        vo.setTop(n.getIsTop() != null && n.getIsTop() == 1);
        vo.setStatus(n.getStatus());
        vo.setPublisherName(n.getPublisherName());
        vo.setPublishTime(n.getPublishTime());
        vo.setCreateTime(n.getCreateTime());
        vo.setRead(readIds.contains(n.getId()));
        LocalDateTime now = LocalDateTime.now();
        vo.setInWindow(n.getEffectiveTime() == null || !n.getEffectiveTime().isAfter(now));
        return vo;
    }
}
