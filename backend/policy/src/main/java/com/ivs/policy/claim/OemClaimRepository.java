package com.ivs.policy.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OemClaimRepository extends JpaRepository<OemClaim, UUID> {
    List<OemClaim> findAllByOrderByCreatedAtDesc();
    List<OemClaim> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId);
    List<OemClaim> findByPeriodCodeOrderByCreatedAtDesc(String periodCode);

    Optional<OemClaim> findFirstBySupplierIdAndPeriodCodeAndPolicyIdAndStatusOrderByCreatedAtAsc(
            UUID supplierId, String periodCode, UUID policyId, String status);

    Optional<OemClaim> findFirstBySupplierIdAndPeriodCodeAndPolicyIdIsNullAndMergedClaimTrueAndStatusOrderByCreatedAtAsc(
            UUID supplierId, String periodCode, String status);

    @Query(value = "select nextval('ivs.claim_no_seq')", nativeQuery = true)
    long nextClaimSequence();
}
