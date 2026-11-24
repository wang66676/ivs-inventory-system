package com.ivs.shared.security;

import java.util.Set;
import java.util.UUID;

public record CurrentActor(
        UUID userId,
        String username,
        String dataScope,
        UUID companyId,
        UUID storeId,
        UUID teamId,
        Set<String> roles
) {
    public boolean isSelfOnly() { return "SELF".equals(dataScope); }
    public boolean isAdmin() { return roles != null && roles.contains("ADMIN"); }
}
