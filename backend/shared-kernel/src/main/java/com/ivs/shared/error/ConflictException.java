package com.ivs.shared.error;

public final class ConflictException extends BusinessException {
    public ConflictException(String code, String message) {
        super(code, message);
    }
}
