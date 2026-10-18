package com.ivs.procurement.purchase;

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

/** 收货单：一次收货一条，明细见 {@link PurchaseReceiptLine}。 */
@Entity
@Table(name = "purchase_receipt", schema = "ivs")
public class PurchaseReceipt {

    @Id
    private UUID id;

    @Column(name = "receipt_no", nullable = false, unique = true, length = 40)
    private String receiptNo;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiptStatus status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** 入库单确认时用返利资金池抵扣的金额（无需审批）。 */
    @Column(name = "offset_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal offsetAmount;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PurchaseReceipt() {
    }

    public static PurchaseReceipt create(String receiptNo, UUID orderId, UUID supplierId, UUID warehouseId,
                                        BigDecimal amount, String remark) {
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.id = UUID.randomUUID();
        receipt.receiptNo = receiptNo;
        receipt.orderId = orderId;
        receipt.supplierId = supplierId;
        receipt.warehouseId = warehouseId;
        receipt.status = ReceiptStatus.PENDING;
        receipt.receivedAt = Instant.now();
        receipt.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        receipt.remark = remark;
        receipt.createdAt = receipt.receivedAt;
        receipt.updatedAt = receipt.createdAt;
        return receipt;
    }

    public void markPosted() {
        this.status = ReceiptStatus.POSTED;
    }

    /** 入库单确认时抵扣车款：金额必须在 0 与本单金额之间。 */
    public void applyOffset(BigDecimal value) {
        BigDecimal offset = (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
        if (offset.signum() < 0 || offset.compareTo(amount) > 0) {
            throw new IllegalArgumentException("抵扣金额必须在 0 与本单金额之间");
        }
        this.offsetAmount = offset;
    }

    /** 应付金额 = 收货金额 − 返利抵扣。 */
    public BigDecimal netAmount() {
        return amount.subtract(offsetAmount == null ? BigDecimal.ZERO.setScale(2) : offsetAmount);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = ReceiptStatus.PENDING;
        }
        if (receivedAt == null) {
            receivedAt = now;
        }
        if (amount == null) {
            amount = BigDecimal.ZERO.setScale(2);
        }
        if (offsetAmount == null) {
            offsetAmount = BigDecimal.ZERO.setScale(2);
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getReceiptNo() { return receiptNo; }
    public UUID getOrderId() { return orderId; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getWarehouseId() { return warehouseId; }
    public ReceiptStatus getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getOffsetAmount() { return offsetAmount == null ? BigDecimal.ZERO.setScale(2) : offsetAmount; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}