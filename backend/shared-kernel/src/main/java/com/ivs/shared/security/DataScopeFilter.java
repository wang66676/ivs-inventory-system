package com.ivs.shared.security;

import java.util.Set;
import java.util.UUID;

public record DataScopeFilter(
        boolean unrestricted,
        Set<UUID> ownerIds
) {
    public DataScopeFilter {
        ownerIds = ownerIds == null ? Set.of() : Set.copyOf(ownerIds);
    }

    public static DataScopeFilter all() { return new DataScopeFilter(true, Set.of()); }
    public static DataScopeFilter owners(Set<UUID> ownerIds) { return new DataScopeFilter(false, ownerIds); }
    public boolean isEmpty() { return !unrestricted && ownerIds.isEmpty(); }
    public boolean allows(java.util.UUID ownerId) { return unrestricted || (ownerId != null && ownerIds.contains(ownerId)); }
}

