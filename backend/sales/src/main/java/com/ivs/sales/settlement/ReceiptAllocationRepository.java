package com.ivs.sales.settlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReceiptAllocationRepository extends JpaRepository<ReceiptAllocation, UUID> {
    List<ReceiptAllocation> findByReceiptId(UUID receiptId);

    List<ReceiptAllocation> findByReceivableId(UUID receivableId);
}