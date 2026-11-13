package com.ivs.procurement.purchase;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByOrderNo(String orderNo);

    boolean existsByOrderNo(String orderNo);

    boolean existsByExternalOrderNoIgnoreCase(String externalOrderNo);

    boolean existsByExternalOrderNoIgnoreCaseAndIdNot(String externalOrderNo, UUID id);

    List<PurchaseOrder> findAllByOrderByCreatedAtDesc();

    List<PurchaseOrder> findByStatusOrderByCreatedAtDesc(PurchaseOrderStatus status);

    @Query("select o from PurchaseOrder o where lower(o.orderNo) like :keyword "
            + "or lower(coalesce(o.externalOrderNo, '')) like :keyword order by o.createdAt desc")
    List<PurchaseOrder> searchByOrderNo(@Param("keyword") String keyword);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "select nextval('ivs.purchase_order_no_seq')", nativeQuery = true)
    long nextOrderSequence();
}

