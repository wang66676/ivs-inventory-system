package com.ivs.inventory.transfer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {
    List<StockTransfer> findAllByOrderByCreatedAtDesc();
    List<StockTransfer> findByFromWarehouseIdOrToWarehouseIdOrderByCreatedAtDesc(UUID from, UUID to);
    List<StockTransfer> findByStatusOrderByCreatedAtDesc(StockTransferStatus status);

    @Query(value = "select nextval('ivs.stock_transfer_no_seq')", nativeQuery = true)
    long nextTransferSequence();
}
