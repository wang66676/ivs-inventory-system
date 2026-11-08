package com.ivs.lease;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface LeaseAssetDisposalRepository extends JpaRepository<LeaseAssetDisposal, UUID> {
    List<LeaseAssetDisposal> findByContractIdOrderByDisposalDateDescCreatedAtDesc(UUID contractId);
    List<LeaseAssetDisposal> findAllByOrderByDisposalDateDescCreatedAtDesc();
    boolean existsBySerialId(UUID serialId);
    @Query(value = "select nextval('ivs.lease_disposal_no_seq')", nativeQuery = true)
    long nextDisposalSequence();
}
