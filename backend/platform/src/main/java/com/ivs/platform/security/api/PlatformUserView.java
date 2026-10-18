package com.ivs.platform.security.api;

import com.ivs.platform.security.DataScope;
import com.ivs.platform.security.PlatformUser;
import com.ivs.platform.security.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record PlatformUserView(
        UUID id,
        String username,
        String displayName,
        DataScope dataScope,
        UUID companyId,
        UUID storeId,
        UUID teamId,
        UserStatus status,
        Set<String> roles,
        Instant createdAt
) {
    public static PlatformUserView from(PlatformUser user, Set<String> roles) {
        return new PlatformUserView(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getDataScope(),
                user.getCompanyId(), user.getStoreId(), user.getTeamId(),
                user.getStatus(), roles, user.getCreatedAt()
        );
    }
}
