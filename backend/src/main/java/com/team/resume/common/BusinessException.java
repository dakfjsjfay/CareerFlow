package com.team.resume.common;

import lombok.Getter;

/**
 * 业务异常：Service 层主动抛出，由 GlobalExceptionHandler 转为统一返回。
 * code 同时用作 HTTP 状态码（400/401/403/404/409/502）。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(400, message);
    }
}
