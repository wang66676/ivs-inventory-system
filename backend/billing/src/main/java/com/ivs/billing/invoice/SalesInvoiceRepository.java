package com.ivs.billing.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, UUID> {
    Optional<SalesInvoice> findByInvoiceNo(String invoiceNo);

    List<SalesInvoice> findAllByOrderByCreatedAtDesc();

    List<SalesInvoice> findByOrderIdOrderByCreatedAtDesc(UUID orderId);

    List<SalesInvoice> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    @Query(value = "select nextval('ivs.sales_invoice_no_seq')", nativeQuery = true)
    long nextInvoiceSequence();
}