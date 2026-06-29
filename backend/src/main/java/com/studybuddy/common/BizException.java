package com.studybuddy.common;

import lombok.Getter;

/** 业务异常，携带错误码与提示。由 GlobalExceptionHandler 统一转换为 R。 */
@Getter
public class BizException extends RuntimeException {
    private final int code;

    public BizException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}
