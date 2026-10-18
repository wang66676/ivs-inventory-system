package com.ivs.policy.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PolicyEntitlementRepository extends JpaRepository<PolicyEntitlement, UUID> {
    List<PolicyEntitlement> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
    List<PolicyEntitlement> findByRetailId(UUID retailId);
    List<PolicyEntitlement> findByPeriodCodeOrderByCreatedAtAsc(String periodCode);
    List<PolicyEntitlement> findAllByOrderByCreatedAtDesc();
    @Query(value = "select nextval('ivs.entitlement_no_seq')", nativeQuery = true)
    long nextEntitlementSequence();
}