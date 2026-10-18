package com.ivs.shared.security;

import java.util.UUID;

public record CurrentActor(
        UUID userId,
        String username,
        String dataScope,
        UUID companyId,
        UUID storeId,
        UUID teamId
) {
    public boolean isSelfOnly() { return "SELF".equals(dataScope); }
}
