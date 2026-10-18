package com.ivs.sales.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SalesDeliveryLineRepository extends JpaRepository<SalesDeliveryLine, UUID> {
    List<SalesDeliveryLine> findByDeliveryId(UUID deliveryId);
}