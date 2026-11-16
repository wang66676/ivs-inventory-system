package com.ivs.billing.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseInvoiceRebateLineRepository extends JpaRepository<PurchaseInvoiceRebateLine, UUID> {
    List<PurchaseInvoiceRebateLine> findByInvoiceIdOrderBySerialNoAsc(UUID invoiceId);
}
