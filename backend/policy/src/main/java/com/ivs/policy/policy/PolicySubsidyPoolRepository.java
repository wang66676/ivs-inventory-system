package com.ivs.policy.policy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PolicySubsidyPoolRepository extends JpaRepository<PolicySubsidyPool, UUID> {
    Optional<PolicySubsidyPool> findByPolicyId(UUID policyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PolicySubsidyPool p where p.policyId = :policyId")
    Optional<PolicySubsidyPool> findByPolicyIdForUpdate(@Param("policyId") UUID policyId);
}