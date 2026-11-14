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
    @Column(name = "invoice_type", length = 100) private String invoiceType;
    @Column(name = "original_invoice_id") private UUID originalInvoiceId;
    @Column(name = "original_invoice_no", length = 100) private String originalInvoiceNo;
    @Column(name = "supplier_id", nullable = false) private UUID supplierId;
    @Column(name = "purchase_order_id") private UUID purchaseOrderId;
    @Column(name = "serial_nos", columnDefinition = "text") private String serialNos;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "matched_amount", nullable = false, precision = 18, scale = 2) private BigDecimal matchedAmount;
    @Column(name = "tax_rate", precision = 8, scale = 4) private BigDecimal taxRate;
    @Column(name = "tax_amount", precision = 18, scale = 2) private BigDecimal taxAmount;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "claim_id") private UUID claimId;
    @Column(name = "external_invoice_no", length = 100) private String externalInvoiceNo;
    @Column(name = "issued_at") private Instant issuedAt;
    @Column(name = "pdf_file_name", length = 255) private String pdfFileName;
    @Column(name = "pdf_storage_key", length = 500) private String pdfStorageKey;
    @Column(name = "extracted_text", columnDefinition = "text") private String extractedText;
    @Column(length = 300) private String remark;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PurchaseInvoice() {
    }

    public static PurchaseInvoice registerRed(String invoiceNo, UUID supplierId, BigDecimal amount,
                                              BigDecimal taxRate, String originalInvoiceNo, String externalInvoiceNo,
                                              UUID claimId, Instant issuedAt, String remark) {
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
        invoice.issuedAt = issuedAt == null ? Instant.now() : issuedAt;
        invoice.remark = remark;
        invoice.createdAt = invoice.issuedAt;
        invoice.updatedAt = invoice.createdAt;
        return invoice;
    }

    public static PurchaseInvoice registerBlue(String invoiceNo, UUID supplierId, UUID purchaseOrderId,
                                               String serialNos, BigDecimal amount, BigDecimal taxRate,
                                               String invoiceType, String externalInvoiceNo, Instant issuedAt, String remark) {
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.id = UUID.randomUUID();
        invoice.invoiceNo = invoiceNo;
        invoice.direction = "IN";
        invoice.type = "BLUE";
        invoice.invoiceType = invoiceType == null || invoiceType.isBlank() ? null : invoiceType.trim();
        invoice.supplierId = supplierId;
        invoice.purchaseOrderId = purchaseOrderId;
        invoice.serialNos = serialNos == null || serialNos.isBlank() ? null : serialNos.trim();
        invoice.amount = money(amount);
        if (invoice.amount.signum() <= 0) throw new IllegalArgumentException("蓝字发票金额必须大于 0");
        invoice.taxRate = taxRate;
        invoice.taxAmount = taxAmountOf(invoice.amount, taxRate);
        invoice.status = "ISSUED";
        invoice.matchedAmount = money(BigDecimal.ZERO);
        invoice.externalInvoiceNo = externalInvoiceNo;
        invoice.issuedAt = issuedAt == null ? Instant.now() : issuedAt;
        invoice.remark = remark;
        invoice.createdAt = Instant.now();
        invoice.updatedAt = invoice.createdAt;
        return invoice;
    }

    public void attachPdf(String fileName, String storageKey, String extractedText) {
        this.pdfFileName = fileName;
        this.pdfStorageKey = storageKey;
        this.extractedText = extractedText;
    }

    public void matchAmount(BigDecimal value) {
        BigDecimal target = money(value);
        if (target.signum() <= 0 || target.compareTo(unmatchedAmount()) > 0) {
            throw new IllegalArgumentException("匹配金额必须大于 0 且不能超过发票未匹配金额");
        }
        matchedAmount = money((matchedAmount == null ? BigDecimal.ZERO : matchedAmount).add(target));
    }

    public void unmatchAmount(BigDecimal value) {
        BigDecimal current = matchedAmount == null ? BigDecimal.ZERO : matchedAmount;
        BigDecimal target = money(value);
        if (target.signum() <= 0 || target.compareTo(current) > 0) {
            throw new IllegalArgumentException("取消匹配金额不合法");
        }
        matchedAmount = money(current.subtract(target));
    }

    public BigDecimal unmatchedAmount() {
        return money(amount.subtract(matchedAmount == null ? BigDecimal.ZERO : matchedAmount));
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
        if (matchedAmount == null) matchedAmount = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getInvoiceNo() { return invoiceNo; }
    public String getDirection() { return direction; }
    public String getType() { return type; }
    public String getInvoiceType() { return invoiceType; }
    public UUID getOriginalInvoiceId() { return originalInvoiceId; }
    public String getOriginalInvoiceNo() { return originalInvoiceNo; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public String getSerialNos() { return serialNos; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getMatchedAmount() { return matchedAmount == null ? money(BigDecimal.ZERO) : matchedAmount; }
    public BigDecimal getUnmatchedAmount() { return unmatchedAmount(); }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public String getStatus() { return status; }
    public UUID getClaimId() { return claimId; }
    public String getExternalInvoiceNo() { return externalInvoiceNo; }
    public Instant getIssuedAt() { return issuedAt; }
    public String getPdfFileName() { return pdfFileName; }
    public String getPdfStorageKey() { return pdfStorageKey; }
    public String getExtractedText() { return extractedText; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
