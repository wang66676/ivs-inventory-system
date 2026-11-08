package com.ivs.lease;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeaseDepositTransactionRepository extends JpaRepository<LeaseDepositTransaction, UUID> {
    List<LeaseDepositTransaction> findByContractIdOrderByTxnDateAscCreatedAtAsc(UUID contractId);
}
