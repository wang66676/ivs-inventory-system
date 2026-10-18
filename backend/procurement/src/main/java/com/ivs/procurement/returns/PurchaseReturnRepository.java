package com.ivs.procurement.returns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, UUID> {
    List<PurchaseReturn> findAllByOrderByCreatedAtDesc();
    List<PurchaseReturn> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId);
    List<PurchaseReturn> findByStatusOrderByCreatedAtDesc(PurchaseReturnStatus status);

    @Query(value = "select nextval('ivs.purchase_return_no_seq')", nativeQuery = true)
    long nextReturnSequence();
}
