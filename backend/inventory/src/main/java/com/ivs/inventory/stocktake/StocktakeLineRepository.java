package com.ivs.inventory.stocktake;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface StocktakeLineRepository extends JpaRepository<StocktakeLine, UUID> {
    List<StocktakeLine> findByStocktakeIdOrderByCreatedAtAsc(UUID stocktakeId);
    @Modifying
    @Query("delete from StocktakeLine l where l.stocktakeId = :stocktakeId")
    int deleteByStocktakeId(@Param("stocktakeId") UUID stocktakeId);
}

