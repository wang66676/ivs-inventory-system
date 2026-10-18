package com.ivs.procurement.returns;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseReturnLineRepository extends JpaRepository<PurchaseReturnLine, UUID> {
    List<PurchaseReturnLine> findByReturnIdOrderByCreatedAtAsc(UUID returnId);
}
