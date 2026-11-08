package com.ivs.lease;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaseBatteryHealthRepository extends JpaRepository<LeaseBatteryHealth, UUID> {
    List<LeaseBatteryHealth> findByContractIdOrderByRecordDateDescCreatedAtDesc(UUID contractId);
    Optional<LeaseBatteryHealth> findFirstByContractIdOrderByRecordDateDescCreatedAtDesc(UUID contractId);
}
