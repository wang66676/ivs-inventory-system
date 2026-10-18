package com.ivs.procurement.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PurchaseReceiptRepository extends JpaRepository<PurchaseReceipt, UUID> {
    List<PurchaseReceipt> findByOrderIdOrderByReceivedAtAsc(UUID orderId);

    List<PurchaseReceipt> findAllByOrderByReceivedAtDesc();

    @Query(value = "select nextval('ivs.purchase_receipt_no_seq')", nativeQuery = true)
    long nextReceiptSequence();
}