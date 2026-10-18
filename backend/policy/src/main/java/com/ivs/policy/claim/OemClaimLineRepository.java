package com.ivs.policy.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OemClaimLineRepository extends JpaRepository<OemClaimLine, UUID> {
    List<OemClaimLine> findByClaimIdOrderByCreatedAtAsc(UUID claimId);
    Optional<OemClaimLine> findByEntitlementId(UUID entitlementId);
}
