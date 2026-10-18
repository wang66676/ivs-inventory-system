package com.ivs.inventory.stock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockBalanceRepository extends JpaRepository<StockBalance, UUID> {

    Optional<StockBalance> findByProductIdAndWarehouseIdAndBatchNo(UUID productId, UUID warehouseId, String batchNo);

    /** 写入余额前取行锁，避免并发入库/出库互相覆盖。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from StockBalance b where b.productId = :productId and b.warehouseId = :warehouseId "
            + "and b.batchNo = :batchNo")
    Optional<StockBalance> findForUpdate(@Param("productId") UUID productId,
                                         @Param("warehouseId") UUID warehouseId,
                                         @Param("batchNo") String batchNo);

    List<StockBalance> findByWarehouseIdOrderByProductIdAsc(UUID warehouseId);

    List<StockBalance> findAllByOrderByUpdatedAtDesc();

    @Query("select b from StockBalance b where b.onHandQty > 0 order by b.updatedAt desc")
    List<StockBalance> findPositiveBalances();
}