package io.github.sandbox.admin.common.result;

import io.github.sandbox.admin.common.exception.ErrorCode;
import lombok.Data;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 统一响应包装（T-0016，design.md §10.1、§4.6）。
 *
 * <p>结构：{@code code / message / data / traceId / timestamp}。
 * {@code code = 0} 表示成功；非 0 为业务语义错误码（见 {@link ErrorCode}）。
 * HTTP 状态与业务码解耦（业务错误仍返回 HTTP 200，由前端拦截器按 code 分发）。</p>
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 业务码（0=成功） */
    private int code;

    /** 人类可读消息，前端直接展示 */
    private String message;

    /** 业务数据负载；分页场景为 {@link PageResult} */
    private T data;

    /** 链路追踪 ID（X-Trace-Id，沿用既有透传机制） */
    private String traceId;

    /** 服务器时间戳（毫秒） */
    private long timestamp;

    private R(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = MDC.get("traceId");
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> R<T> ok() {
        return new R<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), null);
    }

    public static <T> R<T> ok(T data) {
        return new R<>(ErrorCode.SUCCESS.getCode(), ErrorCode.SUCCESS.getMessage(), data);
    }

    public static <T> R<T> ok(T data, String message) {
        return new R<>(ErrorCode.SUCCESS.getCode(), message, data);
    }

    public static <T> R<T> fail(ErrorCode errorCode) {
        return new R<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    public static <T> R<T> fail(ErrorCode errorCode, String message) {
        return new R<>(errorCode.getCode(), message, null);
    }

    public static <T> R<T> fail(int code, String message) {
        return new R<>(code, message, null);
    }
}
