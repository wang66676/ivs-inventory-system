package com.ivs.procurement.purchase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderLineRepository extends JpaRepository<PurchaseOrderLine, UUID> {
    List<PurchaseOrderLine> findByOrderIdOrderByLineNoAsc(UUID orderId);

    void deleteByOrderId(UUID orderId);
}