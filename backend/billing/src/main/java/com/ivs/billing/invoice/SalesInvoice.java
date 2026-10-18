package com.ivs.billing.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 销项发票（一期整车按全价开票）。 */
@Entity
@Table(name = "sales_invoice", schema = "ivs")
public class SalesInvoice {

    @Id
    private UUID id;

    @Column(name = "invoice_no", nullable = false, unique = true, length = 40)
    private String invoiceNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvoiceDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private InvoiceType type;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "tax_rate", precision = 8, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "tax_amount", precision = 18, scale = 2)
    private BigDecimal taxAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(name = "original_invoice_id")
    private UUID originalInvoiceId;

    @Column(name = "external_invoice_no", length = 100)
    private String externalInvoiceNo;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SalesInvoice() {
    }

    public static SalesInvoice create(String invoiceNo, InvoiceDirection direction, InvoiceType type, UUID orderId,
                                      UUID deliveryId, UUID customerId, BigDecimal amount, BigDecimal taxRate,
                                      String remark) {
        SalesInvoice invoice = new SalesInvoice();
        invoice.id = UUID.randomUUID();
        invoice.invoiceNo = invoiceNo;
        invoice.direction = direction == null ? InvoiceDirection.OUT : direction;
        invoice.type = type == null ? InvoiceType.BLUE : type;
        invoice.orderId = orderId;
        invoice.deliveryId = deliveryId;
        invoice.customerId = customerId;
        invoice.amount = money(amount);
        invoice.taxRate = taxRate;
        invoice.taxAmount = taxAmountOf(invoice.amount, taxRate);
        invoice.status = InvoiceStatus.DRAFT;
        invoice.remark = remark;
        invoice.createdAt = Instant.now();
        invoice.updatedAt = invoice.createdAt;
        return invoice;
    }

    /** 按含税金额与税率反算税额。 */
    public static BigDecimal taxAmountOf(BigDecimal grossAmount, BigDecimal taxRate) {
        if (taxRate == null || taxRate.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal net = grossAmount.divide(BigDecimal.ONE.add(taxRate), 2, RoundingMode.HALF_UP);
        return money(grossAmount.subtract(net));
    }

    public void issue(String externalInvoiceNo) {
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("仅草稿发票可以开具");
        }
        if (externalInvoiceNo == null || externalInvoiceNo.isBlank()) {
            throw new IllegalArgumentException("外部发票号不能为空");
        }
        this.status = InvoiceStatus.ISSUED;
        this.externalInvoiceNo = externalInvoiceNo.trim();
        this.issuedAt = Instant.now();
    }

    public void voidInvoice() {
        if (status == InvoiceStatus.VOID) {
            return;
        }
        if (status != InvoiceStatus.DRAFT) {
            throw new IllegalStateException("仅草稿发票可以作废");
        }
        this.status = InvoiceStatus.VOID;
    }

    /** 红冲：仅已开具的蓝票可以红冲；红字发票必须关联原蓝票。 */
    public void redFlush(UUID originalInvoiceId) {
        if (status == InvoiceStatus.RED_FLUSHED) {
            return;
        }
        if (status != InvoiceStatus.ISSUED) {
            throw new IllegalStateException("仅已开具发票可以红冲");
        }
        this.status = InvoiceStatus.RED_FLUSHED;
        if (originalInvoiceId != null) {
            this.originalInvoiceId = originalInvoiceId;
        }
    }

    /** 由红冲动作直接生成的红字发票：创建即为已红冲状态并关联原蓝票。 */
    public void markRedFlushOf(UUID originalInvoiceId) {
        if (type != InvoiceType.RED) {
            throw new IllegalStateException("仅红字发票可以执行该操作");
        }
        if (originalInvoiceId == null) {
            throw new IllegalArgumentException("红字发票必须关联原发票");
        }
        this.originalInvoiceId = originalInvoiceId;
        this.status = InvoiceStatus.RED_FLUSHED;
        this.issuedAt = Instant.now();
    }

    public void assignPricing(BigDecimal grossAmount, BigDecimal rate) {
        this.amount = money(grossAmount);
        this.taxRate = rate;
        this.taxAmount = taxAmountOf(this.amount, rate);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = InvoiceStatus.DRAFT;
        }
        if (amount == null) amount = money(BigDecimal.ZERO);
        if (taxAmount == null) taxAmount = taxAmountOf(amount, taxRate);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getInvoiceNo() { return invoiceNo; }
    public InvoiceDirection getDirection() { return direction; }
    public InvoiceType getType() { return type; }
    public UUID getOrderId() { return orderId; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public InvoiceStatus getStatus() { return status; }
    public UUID getOriginalInvoiceId() { return originalInvoiceId; }
    public String getExternalInvoiceNo() { return externalInvoiceNo; }
    public Instant getIssuedAt() { return issuedAt; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}