package com.ivs.policy.claim;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OemClaimDifferenceRepository extends JpaRepository<OemClaimDifference, UUID> {
    List<OemClaimDifference> findByClaimIdOrderByCreatedAtAsc(UUID claimId);
    List<OemClaimDifference> findAllByOrderByCreatedAtDesc();
    void deleteByClaimId(UUID claimId);
}

