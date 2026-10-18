package com.ivs.policy.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyPreEntitlementRepository extends JpaRepository<PolicyPreEntitlement, UUID> {
    List<PolicyPreEntitlement> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
    List<PolicyPreEntitlement> findAllByOrderByCreatedAtDesc();
    Optional<PolicyPreEntitlement> findByOrderIdAndOrderLineIdAndPolicyId(UUID orderId, UUID orderLineId, UUID policyId);
    @Query(value = "select nextval('ivs.pre_entitlement_no_seq')", nativeQuery = true)
    long nextPreSequence();
}