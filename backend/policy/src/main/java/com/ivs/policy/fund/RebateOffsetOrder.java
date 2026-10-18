package com.ivs.policy.fund;

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

/** 抵扣单：入库单确认时选中返利入池记录抵扣车款，无需审批。 */
@Entity
@Table(name = "rebate_offset_order", schema = "ivs")
public class RebateOffsetOrder {

    @Id
    private UUID id;

    @Column(name = "offset_no", nullable = false, unique = true, length = 40)
    private String offsetNo;

    @Column(name = "pool_id", nullable = false)
    private UUID poolId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "purchase_receipt_id")
    private UUID purchaseReceiptId;

    @Column(name = "receipt_no", length = 40)
    private String receiptNo;

    @Column(name = "offset_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal offsetAmount;

    @Column(name = "applied_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal appliedAmount;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OffsetStatus status;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected RebateOffsetOrder() {
    }

    public static RebateOffsetOrder create(String offsetNo, UUID poolId, UUID supplierId, UUID purchaseOrderId,
                                           UUID purchaseReceiptId, String receiptNo, BigDecimal offsetAmount,
                                           BigDecimal appliedAmount, BigDecimal balanceAfter, UUID createdBy) {
        RebateOffsetOrder order = new RebateOffsetOrder();
        order.id = UUID.randomUUID();
        order.offsetNo = offsetNo;
        order.poolId = poolId;
        order.supplierId = supplierId;
        order.purchaseOrderId = purchaseOrderId;
        order.purchaseReceiptId = purchaseReceiptId;
        order.receiptNo = receiptNo;
        order.offsetAmount = money(offsetAmount);
        order.appliedAmount = money(appliedAmount);
        order.balanceAfter = money(balanceAfter);
        order.status = OffsetStatus.ACTIVE;
        order.createdBy = createdBy;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        return order;
    }

    public void cancel() {
        if (status == OffsetStatus.CANCELLED) {
            return;
        }
        status = OffsetStatus.CANCELLED;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = OffsetStatus.ACTIVE;
        if (offsetAmount == null) offsetAmount = money(BigDecimal.ZERO);
        if (appliedAmount == null) appliedAmount = money(BigDecimal.ZERO);
        if (balanceAfter == null) balanceAfter = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getOffsetNo() { return offsetNo; }
    public UUID getPoolId() { return poolId; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public UUID getPurchaseReceiptId() { return purchaseReceiptId; }
    public String getReceiptNo() { return receiptNo; }
    public BigDecimal getOffsetAmount() { return offsetAmount; }
    public BigDecimal getAppliedAmount() { return appliedAmount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public OffsetStatus getStatus() { return status; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}