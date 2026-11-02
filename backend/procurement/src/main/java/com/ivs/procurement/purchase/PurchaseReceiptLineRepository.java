package com.ivs.procurement.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseReceiptLineRepository extends JpaRepository<PurchaseReceiptLine, UUID> {
    List<PurchaseReceiptLine> findByReceiptId(UUID receiptId);

    Optional<PurchaseReceiptLine> findByIdAndReceiptId(UUID id, UUID receiptId);

    @Query("select l from PurchaseReceiptLine l where lower(coalesce(l.serialNos, '')) like lower(concat('%', :serialNo, '%'))")
    List<PurchaseReceiptLine> searchBySerialNo(@Param("serialNo") String serialNo);
}
