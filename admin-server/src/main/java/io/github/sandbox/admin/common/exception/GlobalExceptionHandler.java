package io.github.sandbox.admin.common.exception;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import io.github.sandbox.admin.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器（T-0016，design.md §4.6）。
 *
 * <p>HTTP 状态与业务码解耦：统一返回 HTTP 200 + 业务语义 code，
 * 由前端 Axios 拦截器按 code 分发（401/403 语义由 code 2xxxx 表达）。</p>
 *
 * <p>认证授权语义互不混淆：
 * 未登录 / 无权限 / 角色不足 / 账号停用 / 被踢下线 分别映射 20001 / 20002 / 20003 / 11004 / 20004。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：透出自定义 code 与安全 message，不暴露内部对象 */
    @ExceptionHandler(BusinessException.class)
    public R<Void> handleBusiness(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常 [{}] {} -> code={}, msg={}", request.getMethod(), request.getRequestURI(), e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /** Sa-Token：未登录（区分被踢下线 token 状态） */
    @ExceptionHandler(NotLoginException.class)
    public R<Void> handleNotLogin(NotLoginException e) {
        if (NotLoginException.BE_REPLACED.equals(e.getType())
                || NotLoginException.KICK_OUT.equals(e.getType())) {
            return R.fail(ErrorCode.TOKEN_KICKED_OUT);
        }
        return R.fail(ErrorCode.NOT_LOGIN);
    }

    /** Sa-Token：无按钮/接口权限 */
    @ExceptionHandler(NotPermissionException.class)
    public R<Void> handleNotPermission(NotPermissionException e) {
        log.warn("权限不足: {}", e.getPermission());
        return R.fail(ErrorCode.NO_PERMISSION);
    }

    /** Sa-Token：角色不足 */
    @ExceptionHandler(NotRoleException.class)
    public R<Void> handleNotRole(NotRoleException e) {
        log.warn("角色不足: {}", e.getRole());
        return R.fail(ErrorCode.NO_ROLE);
    }

    /** Sa-Token：账号被封禁（停用语义） */
    @ExceptionHandler(DisableServiceException.class)
    public R<Void> handleDisabled(DisableServiceException e) {
        return R.fail(ErrorCode.ACCOUNT_DISABLED);
    }

    /** @RequestBody 上的 @Valid 校验失败：字段级错误 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return R.fail(ErrorCode.PARAM_ERROR, msg.isEmpty() ? ErrorCode.PARAM_ERROR.getMessage() : msg);
    }

    /** 表单绑定 / @Validated 方法参数校验失败 */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBind(BindException e) {
        String msg = e.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return R.fail(ErrorCode.PARAM_ERROR, msg.isEmpty() ? ErrorCode.PARAM_ERROR.getMessage() : msg);
    }

    /** 缺少必填请求参数 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        return R.fail(ErrorCode.PARAM_ERROR, "缺少必填参数: " + e.getParameterName());
    }

    /** 参数类型不匹配 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return R.fail(ErrorCode.PARAM_ERROR, "参数类型错误: " + e.getName());
    }

    /** 请求体不可读 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        return R.fail(ErrorCode.PARAM_ERROR, "请求体格式错误");
    }

    /** 兜底：不暴露堆栈 */
    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e, HttpServletRequest request) {
        log.error("系统异常 [{}] {}", request.getMethod(), request.getRequestURI(), e);
        return R.fail(ErrorCode.SYSTEM_ERROR);
    }
}
