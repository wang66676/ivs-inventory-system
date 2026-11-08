package com.ivs.lease;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LeaseContractChangeRepository extends JpaRepository<LeaseContractChange, UUID> {
    List<LeaseContractChange> findByContractIdOrderByCreatedAtAsc(UUID contractId);
}
