package com.ivs.platform.security;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        UUID sessionId,
        String username,
        String displayName,
        DataScope dataScope,
        UUID companyId,
        UUID storeId,
        UUID teamId,
        Set<String> roles,
        Set<String> permissions
) {
}
