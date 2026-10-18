package com.ivs.platform.security.api;

import java.util.Set;
import java.util.UUID;

public record LoginResponse(
        String token,
        UUID userId,
        String username,
        String displayName,
        String dataScope,
        Set<String> roles,
        Set<String> permissions
) {
}
