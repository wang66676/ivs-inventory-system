package com.ivs.policy.policy;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PolicyPreEntitlementRepository extends JpaRepository<PolicyPreEntitlement, UUID> {
    List<PolicyPreEntitlement> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
    List<PolicyPreEntitlement> findAllByOrderByCreatedAtDesc();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PolicyPreEntitlement p where p.orderId = :orderId order by p.createdAt asc")
    List<PolicyPreEntitlement> findByOrderIdForUpdate(UUID orderId);
    Optional<PolicyPreEntitlement> findByOrderIdAndOrderLineIdAndSerialIdAndPolicyId(
            UUID orderId, UUID orderLineId, UUID serialId, UUID policyId);
    @Query(value = "select nextval('ivs.pre_entitlement_no_seq')", nativeQuery = true)
    long nextPreSequence();
}
