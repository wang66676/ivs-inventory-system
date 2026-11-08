package com.ivs.lease;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeaseDepreciationScheduleRepository extends JpaRepository<LeaseDepreciationSchedule, UUID> {
    List<LeaseDepreciationSchedule> findByContractIdOrderByPeriodNoAsc(UUID contractId);
    boolean existsByContractId(UUID contractId);
}
