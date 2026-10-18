package com.ivs.inventory.stock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLedgerRepository extends JpaRepository<StockLedger, UUID> {

    List<StockLedger> findTop200ByOrderByOccurredAtDesc();
    List<StockLedger> findAllByOrderByOccurredAtAsc();

    List<StockLedger> findByProductIdOrderByOccurredAtDesc(UUID productId);

    List<StockLedger> findBySerialIdOrderByOccurredAtDesc(UUID serialId);

    List<StockLedger> findByBizTypeAndBizIdOrderByOccurredAtAsc(String bizType, UUID bizId);

    Optional<StockLedger> findTopBySerialIdAndDirectionOrderByOccurredAtDesc(UUID serialId, StockDirection direction);

    long countByProductIdAndSerialIdIsNotNull(UUID productId);
}
