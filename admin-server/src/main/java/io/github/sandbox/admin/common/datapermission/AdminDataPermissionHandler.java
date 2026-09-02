package io.github.sandbox.admin.common.datapermission;

import com.baomidou.mybatisplus.extension.plugins.handler.MultiDataPermissionHandler;
import io.github.sandbox.admin.common.security.AdminLoginUser;
import io.github.sandbox.admin.common.security.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.schema.Table;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 管理端数据权限拦截器（T-0021，design.md §5.4/§5.5；er-alignment.md §3 归属键口径）。
 *
 * <p>行级过滤规则（按表注册，集中维护，Mapper XML 不硬编码）：</p>
 * <ul>
 *   <li>{@code client_app}：{@code owner_user_id = 当前用户}</li>
 *   <li>{@code client_api_key}：{@code bound_user_id = 当前用户
 *       OR (bound_user_id IS NULL AND client_id IN (SELECT id FROM client_app WHERE owner_user_id = 当前用户 AND deleted = 0))}
 *       —— 与 er-alignment.md §3 解析口径 {@code owner = COALESCE(bound_user_id, client_app.owner_user_id)} 等价</li>
 *   <li>{@code api_log} / {@code sandbox_operation_log}：{@code owner_user_id = 当前用户}</li>
 * </ul>
 *
 * <p>superadmin / admin / auditor 命中 ALL 可见域不加任何过滤；
 * 管理端元数据表（admin_* / sys_config / ratelimit_rule）不在注册表内，天然不受本机制作用（T-0021 验收）。
 * 未登录链路（如登录查账号）以"无登录上下文即跳过"保证不误作用。</p>
 *
 * <p>批次3 复用方式：向 {@link #SELF_FILTER_TABLES} 增补归属表即自动生效；
 * 需要跨归属的系统级查询用 {@link DataPermissionIgnoreHolder#runIgnored} 包裹。</p>
 */
@Slf4j
@Component
public class AdminDataPermissionHandler implements MultiDataPermissionHandler {

    /** 受 SELF 数据权限管辖的表（小写表名）。批次3 新增归属表在此登记。 */
    private static final List<String> SELF_FILTER_TABLES = List.of(
            "client_app", "client_api_key", "api_log", "sandbox_operation_log",
            "codeguard_detect_log"
    );

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        if (DataPermissionIgnoreHolder.isIgnored()) {
            return null;
        }
        String tableName = table.getName().replace("`", "").toLowerCase();
        if (!SELF_FILTER_TABLES.contains(tableName)) {
            return null; // 元数据表不作用
        }
        AdminLoginUser user = SecurityUtils.getLoginUserQuietly();
        if (user == null || user.isAllScope() || user.getUserId() == null) {
            return null; // 未登录（登录链路）与 ALL 可见域不加过滤
        }
        long userId = user.getUserId();
        String alias = table.getAlias() != null && !table.getAlias().getName().isBlank()
                ? table.getAlias().getName() : table.getName();

        String condition = switch (tableName) {
            case "client_app" -> alias + ".owner_user_id = " + userId;
            case "client_api_key" -> "(" + alias + ".bound_user_id = " + userId
                    + " OR (" + alias + ".bound_user_id IS NULL AND " + alias + ".client_id IN"
                    + " (SELECT id FROM client_app WHERE owner_user_id = " + userId + " AND deleted = 0)))";
            case "api_log", "sandbox_operation_log", "codeguard_detect_log" -> alias + ".owner_user_id = " + userId;
            default -> null;
        };
        if (condition == null) {
            return null;
        }
        try {
            return CCJSqlParserUtil.parseCondExpression(condition);
        } catch (Exception e) {
            log.error("数据权限过滤构造失败 table={}，按最小权限拒绝该查询", tableName, e);
            // 构造异常时给出恒假条件，防止越权放行
            try {
                return CCJSqlParserUtil.parseCondExpression("1 = 0");
            } catch (Exception ignored) {
                return null;
            }
        }
    }
}
