package com.ivs.sales.returns;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface SalesReturnRepository extends JpaRepository<SalesReturn, UUID> {
    List<SalesReturn> findAllByOrderByCreatedAtDesc();
    List<SalesReturn> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
    List<SalesReturn> findByDeliveryIdOrderByCreatedAtDesc(UUID deliveryId);
    List<SalesReturn> findByStatusOrderByCreatedAtDesc(SalesReturnStatus status);

    @Query(value = "select nextval('ivs.sales_return_no_seq')", nativeQuery = true)
    long nextReturnSequence();
}
