package com.ivs.lease;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface LeaseScheduleRepository extends JpaRepository<LeaseSchedule, UUID> {
    List<LeaseSchedule> findByContractIdOrderByPeriodNoAsc(UUID contractId);
    List<LeaseSchedule> findByContractIdAndDueDateLessThanEqualAndReceivableIdIsNullOrderByPeriodNoAsc(
            UUID contractId, LocalDate dueDate);
}
