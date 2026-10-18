package com.ivs.policy.cost;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyCostAllocationRepository extends JpaRepository<PolicyCostAllocation, UUID> {
    Optional<PolicyCostAllocation> findByEntitlementId(UUID entitlementId);
    List<PolicyCostAllocation> findByPeriodCodeOrderByCreatedAtAsc(String periodCode);
    List<PolicyCostAllocation> findAllByOrderByCreatedAtDesc();
}
