package com.ivs.platform.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Optional<UserSession> findByTokenHash(String tokenHash);

    List<UserSession> findByUserIdAndRevokedAtIsNull(UUID userId);
}
