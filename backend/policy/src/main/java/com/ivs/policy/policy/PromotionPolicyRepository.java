package com.ivs.policy.policy;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromotionPolicyRepository extends JpaRepository<PromotionPolicy, UUID> {
    Optional<PromotionPolicy> findByPolicyNo(String policyNo);
    List<PromotionPolicy> findAllByOrderByPriorityDescCreatedAtDesc();
    List<PromotionPolicy> findByStatusOrderByPriorityDescCreatedAtDesc(PolicyStatus status);
    @Query(value = "select nextval('ivs.policy_no_seq')", nativeQuery = true)
    long nextPolicySequence();
}