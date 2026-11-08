package com.ivs.lease;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LeaseSettlementRepository extends JpaRepository<LeaseSettlement, UUID> {
    Optional<LeaseSettlement> findByContractId(UUID contractId);
}
