package io.github.sandbox.admin.common.exception;

import lombok.Getter;

/**
 * 错误码体系（T-0016，design.md §10.2）。
 *
 * <p>分段约定：</p>
 * <ul>
 *   <li>{@code 0} — 成功</li>
 *   <li>{@code 1xxxx} — 通用业务错误（参数、业务规则）</li>
 *   <li>{@code 2xxxx} — 鉴权与权限（未登录 / 无权限 / 角色不足 / 账号停用 / 被踢下线）</li>
 *   <li>{@code 3xxxx} — 客户端 / ApiKey / 限流（本批次仅登记骨架，批次3/4 使用）</li>
 *   <li>{@code 4xxxx} — 会话（批次4 使用，此处登记骨架）</li>
 *   <li>{@code 5xxxx} — 系统内部错误（兜底）</li>
 * </ul>
 */
@Getter
public enum ErrorCode {

    // ===== 0 成功 =====
    SUCCESS(0, "ok"),

    // ===== 1xxxx 通用业务 =====
    PARAM_ERROR(10001, "请求参数错误"),
    BUSINESS_ERROR(10002, "业务处理失败"),
    DATA_NOT_FOUND(10003, "数据不存在"),
    DATA_ALREADY_EXISTS(10004, "数据已存在"),

    // ----- 认证类业务（1-1xxx 细分：验证码/登录/密码） -----
    CAPTCHA_ERROR(11001, "验证码错误或已失效"),
    BAD_CREDENTIALS(11002, "用户名或密码错误"),
    ACCOUNT_LOCKED(11003, "账号已锁定，请稍后再试"),
    ACCOUNT_DISABLED(11004, "账号已停用"),
    OLD_PASSWORD_INCORRECT(11005, "旧密码不正确"),
    PASSWORD_NOT_MATCH_CONFIRM(11006, "两次输入的密码不一致"),
    FIRST_LOGIN_REQUIRED(11007, "首次登录必须修改密码"),
    CONFIG_KEY_UNKNOWN(11008, "未识别的系统设置键"),
    CONFIG_VALUE_INVALID(11009, "系统设置值不符合类型约束"),

    // ----- RBAC 元数据类业务（1-2xxx） -----
    USERNAME_EXISTS(12001, "用户名已存在"),
    ROLE_KEY_EXISTS(12002, "角色权限字符已存在"),
    BUILT_IN_ROLE_PROTECTED(12003, "内置角色不允许删除或修改权限字符"),
    ROLE_IN_USE(12004, "角色已被用户引用，无法删除"),
    MENU_HAS_CHILDREN(12005, "菜单存在子节点，无法删除"),
    USER_HAS_ACTIVE_RESOURCE(12006, "用户仍持有有效会话或启用中的ApiKey，禁止删除"),
    LAST_SUPERADMIN_PROTECTED(12007, "不允许禁用或删除最后一个超级管理员"),
    NO_SELF_DISABLE(12008, "不允许停用或注销当前登录账号"),

    // ===== 2xxxx 鉴权与权限 =====
    NOT_LOGIN(20001, "未登录或登录已过期"),
    NO_PERMISSION(20002, "无操作权限"),
    NO_ROLE(20003, "角色不足"),
    TOKEN_KICKED_OUT(20004, "账号已在其他设备登录，当前会话已失效"),
    TOKEN_INVALID(20005, "凭证无效"),

    // ===== 3xxxx 客户端 / ApiKey / 限流 =====
    API_KEY_MISSING(30001, "缺少 ApiKey"),
    API_KEY_NOT_FOUND(30002, "ApiKey 不存在"),
    API_KEY_INVALID(30003, "ApiKey 已撤销、过期或未生效"),
    CLIENT_DISABLED(30004, "客户端已停用"),
    USER_DISABLED(30005, "归属用户已停用"),
    RATE_LIMIT_EXCEEDED(30006, "请求超出限流阈值"),
    CLIENT_CODE_EXISTS(30007, "客户端编码已存在"),
    CLIENT_HAS_ACTIVE_KEYS(30008, "客户端仍持有有效 ApiKey，禁止删除，请先撤销或处理"),
    API_KEY_STATE_CONFLICT(30009, "ApiKey 当前状态不允许该操作"),
    RATELIMIT_RULE_CONFLICT(30010, "相同维度、目标、窗口与阈值的规则已存在"),
    RATELIMIT_TARGET_INVALID(30011, "限流规则目标不存在或不可见"),

    // ===== 4xxxx 会话 =====
    SESSION_NOT_FOUND(40001, "会话不存在或已销毁"),
    SESSION_DESTROY_FAILED(40002, "会话强制销毁失败"),

    // ===== 5xxxx 系统内部 =====
    SYSTEM_ERROR(50000, "系统繁忙，请稍后再试"),
    SANDBOX_BRIDGE_ERROR(50001, "调用沙箱服务内部接口失败"),
    SANDBOX_BRIDGE_UNAUTHORIZED(50002, "沙箱服务拒绝内部凭证，请检查内部共享密钥配置");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
