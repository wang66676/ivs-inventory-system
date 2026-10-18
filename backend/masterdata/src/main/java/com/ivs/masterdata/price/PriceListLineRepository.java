package com.ivs.masterdata.price;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PriceListLineRepository extends JpaRepository<PriceListLine, UUID> {
    List<PriceListLine> findByPriceListIdOrderByCreatedAtAsc(UUID priceListId);

    Optional<PriceListLine> findByPriceListIdAndProductId(UUID priceListId, UUID productId);

    void deleteByPriceListId(UUID priceListId);

    void deleteByProductId(UUID productId);
}