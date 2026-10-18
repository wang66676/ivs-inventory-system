package com.ivs.shared.error;

public final class NotFoundException extends BusinessException {
    public NotFoundException(String resource, Object id) {
        super("NOT_FOUND", resource + "不存在: " + id);
    }
}
