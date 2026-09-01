package io.github.sandbox.admin.common.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import io.github.sandbox.admin.common.security.AdminLoginUser;
import io.github.sandbox.admin.common.security.SecurityUtils;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis Plus 公共字段自动填充（T-0009，design.md §4.4）。
 *
 * <ul>
 *   <li>插入：create_time / update_time / create_by / update_by</li>
 *   <li>更新：update_time / update_by</li>
 *   <li>create_by / update_by 从 Sa-Token 当前登录用户解析；
 *       登录前（如验证码/种子流程内的系统级写入）口径为 {@code system}。</li>
 * </ul>
 */
@Component
public class AdminMetaObjectHandler implements MetaObjectHandler {

    /** 登录前/系统级操作归属口径 */
    public static final String SYSTEM_USER = "system";

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        String operator = currentOperator();
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "createBy", String.class, operator);
        strictInsertFill(metaObject, "updateBy", String.class, operator);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        strictUpdateFill(metaObject, "updateBy", String.class, currentOperator());
    }

    private String currentOperator() {
        AdminLoginUser user = SecurityUtils.getLoginUserQuietly();
        return user == null ? SYSTEM_USER : user.getUsername();
    }
}
