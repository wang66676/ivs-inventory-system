package com.ivs.inventory.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StockLockRepository extends JpaRepository<StockLock, UUID> {

    List<StockLock> findByStatus(StockLockStatus status);

    List<StockLock> findByStatusAndExpiresAtBefore(StockLockStatus status, Instant moment);

    List<StockLock> findByBizIdOrderByCreatedAtAsc(UUID bizId);

    List<StockLock> findByProductIdAndWarehouseIdAndStatus(UUID productId, UUID warehouseId, StockLockStatus status);
}