package com.ivs.platform.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlatformUserRepository extends JpaRepository<PlatformUser, UUID> {
    Optional<PlatformUser> findByUsername(String username);

    List<PlatformUser> findAllByOrderByCreatedAtDesc();

    boolean existsByUsername(String username);

    List<PlatformUser> findByCompanyId(UUID companyId);

    List<PlatformUser> findByStoreId(UUID storeId);

    List<PlatformUser> findByTeamId(UUID teamId);
}


