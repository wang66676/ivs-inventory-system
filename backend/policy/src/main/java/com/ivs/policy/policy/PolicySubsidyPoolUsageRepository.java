package com.ivs.policy.policy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PolicySubsidyPoolUsageRepository extends JpaRepository<PolicySubsidyPoolUsage, UUID> {
    List<PolicySubsidyPoolUsage> findByPoolIdOrderByUsedAtAsc(UUID poolId);
}
