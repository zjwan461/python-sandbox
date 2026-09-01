package io.github.sandbox.admin.common.exception;

import lombok.Getter;

/**
 * 业务异常（T-0016，design.md §4.6）。
 *
 * <p>Service 层业务规则校验失败时抛出；由 {@code GlobalExceptionHandler}
 * 翻译为统一响应 {@code R.fail(code, message)}，不暴露内部对象。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    /** 便捷构造：直接使用错误码的消息 */
    public static BusinessException of(ErrorCode errorCode) {
        return new BusinessException(errorCode);
    }
}
