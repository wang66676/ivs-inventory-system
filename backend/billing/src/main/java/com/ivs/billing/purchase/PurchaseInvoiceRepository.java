package com.ivs.billing.purchase;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, UUID> {
    List<PurchaseInvoice> findAllByOrderByCreatedAtDesc();
    List<PurchaseInvoice> findBySupplierIdOrderByCreatedAtDesc(UUID supplierId);
    @Query(value = "select nextval('ivs.purchase_invoice_no_seq')", nativeQuery = true)
    long nextInvoiceSequence();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PurchaseInvoice i where i.id = :id")
    java.util.Optional<PurchaseInvoice> findByIdForUpdate(@Param("id") java.util.UUID id);
}
