package com.ivs.sales.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {
    Optional<SalesOrder> findByOrderNo(String orderNo);

    List<SalesOrder> findAllByOrderByCreatedAtDesc();

    List<SalesOrder> findByStatusOrderByCreatedAtDesc(SalesOrderStatus status);

    List<SalesOrder> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @Query("select o from SalesOrder o where lower(o.orderNo) like :keyword order by o.createdAt desc")
    List<SalesOrder> searchByOrderNo(@Param("keyword") String keyword);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select nextval('ivs.sales_order_no_seq')", nativeQuery = true)
    long nextOrderSequence();
}