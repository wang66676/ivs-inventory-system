package com.ivs.sales.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {
    List<Receipt> findAllByOrderByReceivedAtDesc();

    List<Receipt> findByCustomerIdOrderByReceivedAtDesc(UUID customerId);

    @Query(value = "select nextval('ivs.receipt_no_seq')", nativeQuery = true)
    long nextReceiptSequence();
}