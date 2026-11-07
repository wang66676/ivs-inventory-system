package com.ivs.app.matching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 蓝字进项发票与应付单的匹配明细（app 层协调，不使业务模块互相依赖）。 */
@Entity
@Table(name = "payable_invoice_match", schema = "ivs")
public class PayableInvoiceMatch {
    @Id
    private UUID id;
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    @Column(name = "payable_id", nullable = false)
    private UUID payableId;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PayableInvoiceMatch() {
    }

    public static PayableInvoiceMatch create(UUID invoiceId, UUID payableId, BigDecimal amount) {
        PayableInvoiceMatch match = new PayableInvoiceMatch();
        match.id = UUID.randomUUID();
        match.invoiceId = invoiceId;
        match.payableId = payableId;
        match.amount = amount;
        match.createdAt = Instant.now();
        return match;
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public UUID getPayableId() { return payableId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
