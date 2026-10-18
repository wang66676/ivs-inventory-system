package com.ivs.inventory.stocktake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface StocktakeRepository extends JpaRepository<Stocktake, UUID> {
    List<Stocktake> findAllByOrderByCreatedAtDesc();
    List<Stocktake> findByWarehouseIdOrderByCreatedAtDesc(UUID warehouseId);
    List<Stocktake> findByStatusOrderByCreatedAtDesc(StocktakeStatus status);

    @Query(value = "select nextval('ivs.stocktake_no_seq')", nativeQuery = true)
    long nextStocktakeSequence();
}
