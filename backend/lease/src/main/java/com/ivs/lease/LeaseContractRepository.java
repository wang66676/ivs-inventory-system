package com.ivs.lease;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LeaseContractRepository extends JpaRepository<LeaseContract, UUID> {
    List<LeaseContract> findAllByOrderByCreatedAtDesc();
    List<LeaseContract> findByStatusOrderByCreatedAtAsc(LeaseStatus status);
    boolean existsBySerialIdAndStatus(UUID serialId, LeaseStatus status);
    @Query(value = "select nextval('ivs.lease_contract_no_seq')", nativeQuery = true)
    long nextContractSequence();
}
