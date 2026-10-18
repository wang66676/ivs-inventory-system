package com.ivs.shared.error;

/**
 * 可预期的业务异常，由 API 层转换为稳定的业务错误码。
 */
public class BusinessException extends RuntimeException {
    private final String code;

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
