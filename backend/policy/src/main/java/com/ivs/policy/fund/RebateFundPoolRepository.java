package com.ivs.policy.fund;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RebateFundPoolRepository extends JpaRepository<RebateFundPool, UUID> {
    Optional<RebateFundPool> findByPoolCode(String poolCode);
}