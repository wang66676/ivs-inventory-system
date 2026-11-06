package com.ivs.sales.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InstallmentScheduleRepository extends JpaRepository<InstallmentSchedule, UUID> {
    List<InstallmentSchedule> findByPlanIdOrderByPeriodNoAsc(UUID planId);

    Optional<InstallmentSchedule> findByReceivableId(UUID receivableId);
}
