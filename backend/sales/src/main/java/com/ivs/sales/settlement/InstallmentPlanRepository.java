package com.ivs.sales.settlement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallmentPlanRepository extends JpaRepository<InstallmentPlan, UUID> {
    List<InstallmentPlan> findAllByOrderByCreatedAtDesc();

    Optional<InstallmentPlan> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InstallmentPlan p where p.id = :id")
    Optional<InstallmentPlan> findByIdForUpdate(UUID id);

    @Query(value = "select nextval('ivs.installment_plan_no_seq')", nativeQuery = true)
    long nextPlanSequence();
}
