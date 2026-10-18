package com.ivs.billing.purchase;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 进项发票（厂家 → 我方）。红字发票登记后自动进入返利资金池。 */
@Entity
@Table(name = "purchase_invoice", schema = "ivs")
public class PurchaseInvoice {

    @Id private UUID id;
    @Column(name = "invoice_no", nullable = false, unique = true, length = 40) private String invoiceNo;
    @Column(nullable = false, length = 10) private String direction;
    @Column(nullable = false, length = 10) private String type;
    @Column(name = "original_invoice_id") private UUID originalInvoiceId;
    @Column(name = "original_invoice_no", length = 100) private String originalInvoiceNo;
    @Column(name = "supplier_id", nullable = false) private UUID supplierId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "tax_rate", precision = 8, scale = 4) private BigDecimal taxRate;
    @Column(name = "tax_amount", precision = 18, scale = 2) private BigDecimal taxAmount;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "claim_id") private UUID claimId;
    @Column(name = "external_invoice_no", length = 100) private String externalInvoiceNo;
    @Column(name = "issued_at") private Instant issuedAt;
    @Column(length = 300) private String remark;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PurchaseInvoice() {
    }

    public static PurchaseInvoice registerRed(String invoiceNo, UUID supplierId, BigDecimal amount,
                                              BigDecimal taxRate, String originalInvoiceNo, String externalInvoiceNo,
                                              UUID claimId, String remark) {
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.id = UUID.randomUUID();
        invoice.invoiceNo = invoiceNo;
        invoice.direction = "IN";
        invoice.type = "RED";
        invoice.supplierId = supplierId;
        invoice.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        if (invoice.amount.signum() <= 0) {
            throw new IllegalArgumentException("红字发票金额必须大于 0");
        }
        invoice.taxRate = taxRate;
        invoice.taxAmount = taxAmountOf(invoice.amount, taxRate);
        invoice.status = "ISSUED";
        invoice.originalInvoiceNo = originalInvoiceNo;
        invoice.externalInvoiceNo = externalInvoiceNo;
        invoice.claimId = claimId;
        invoice.issuedAt = Instant.now();
        invoice.remark = remark;
        invoice.createdAt = invoice.issuedAt;
        invoice.updatedAt = invoice.createdAt;
        return invoice;
    }

    public static BigDecimal taxAmountOf(BigDecimal gross, BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) return BigDecimal.ZERO.setScale(2);
        BigDecimal net = gross.divide(BigDecimal.ONE.add(rate), 2, RoundingMode.HALF_UP);
        return gross.subtract(net).setScale(2, RoundingMode.HALF_UP);
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = "ISSUED";
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getInvoiceNo() { return invoiceNo; }
    public String getDirection() { return direction; }
    public String getType() { return type; }
    public UUID getOriginalInvoiceId() { return originalInvoiceId; }
    public String getOriginalInvoiceNo() { return originalInvoiceNo; }
    public UUID getSupplierId() { return supplierId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public String getStatus() { return status; }
    public UUID getClaimId() { return claimId; }
    public String getExternalInvoiceNo() { return externalInvoiceNo; }
    public Instant getIssuedAt() { return issuedAt; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}