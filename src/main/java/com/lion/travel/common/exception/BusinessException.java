package com.lion.travel.common.exception;

import lombok.Getter;

/**
 * 业务异常。
 *
 * <p>业务逻辑校验失败时抛出,由 {@link GlobalExceptionHandler} 统一捕获并转换为
 * 友好提示返回给调用方,避免直接暴露堆栈。</p>
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 业务错误码 */
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }
}
