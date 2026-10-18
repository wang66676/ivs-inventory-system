package com.ivs.platform.security.api;

import com.ivs.platform.security.AuthenticatedUser;

import java.util.Set;
import java.util.UUID;

public record CurrentUserView(
        UUID userId,
        String username,
        String displayName,
        String dataScope,
        Set<String> roles,
        Set<String> permissions,
        UUID companyId,
        UUID storeId,
        UUID teamId
) {
    public static CurrentUserView from(AuthenticatedUser user) {
        return new CurrentUserView(
                user.userId(), user.username(), user.displayName(), user.dataScope().name(),
                user.roles(), user.permissions(), user.companyId(), user.storeId(), user.teamId()
        );
    }
}
