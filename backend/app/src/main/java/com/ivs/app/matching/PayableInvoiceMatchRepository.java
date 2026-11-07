package com.ivs.app.matching;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PayableInvoiceMatchRepository extends JpaRepository<PayableInvoiceMatch, UUID> {
    List<PayableInvoiceMatch> findByInvoiceIdOrderByCreatedAtAsc(UUID invoiceId);
}
