package com.ivs.inventory.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StockTransferLineRepository extends JpaRepository<StockTransferLine, UUID> {
    List<StockTransferLine> findByTransferIdOrderByCreatedAtAsc(UUID transferId);

    @Modifying
    @Query("delete from StockTransferLine l where l.transferId = :transferId")
    int deleteByTransferId(@Param("transferId") UUID transferId);
}
