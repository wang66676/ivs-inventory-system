package com.ivs.sales.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SalesDeliveryRepository extends JpaRepository<SalesDelivery, UUID> {
    List<SalesDelivery> findAllByOrderByDeliveredAtDesc();

    List<SalesDelivery> findByOrderIdOrderByDeliveredAtAsc(UUID orderId);

    List<SalesDelivery> findByCustomerIdOrderByDeliveredAtDesc(UUID customerId);

    @Query(value = "select nextval('ivs.sales_delivery_no_seq')", nativeQuery = true)
    long nextDeliverySequence();
}