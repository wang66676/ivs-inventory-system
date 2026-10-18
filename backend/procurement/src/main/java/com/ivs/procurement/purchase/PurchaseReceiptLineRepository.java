package com.ivs.procurement.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseReceiptLineRepository extends JpaRepository<PurchaseReceiptLine, UUID> {
    List<PurchaseReceiptLine> findByReceiptId(UUID receiptId);
}