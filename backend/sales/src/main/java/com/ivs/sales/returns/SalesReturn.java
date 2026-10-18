package com.ivs.sales.returns;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 销售退货单：草稿 → 已过账；过账时回库、恢复库存并冲减应收。 */
@Entity
@Table(name = "sales_return", schema = "ivs")
public class SalesReturn {

    @Id private UUID id;
    @Column(name = "return_no", nullable = false, unique = true, length = 40) private String returnNo;
    @Column(name = "customer_id", nullable = false) private UUID customerId;
    @Column(name = "delivery_id") private UUID deliveryId;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private SalesReturnStatus status;
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2) private BigDecimal totalAmount;
    @Column(name = "cost_amount", nullable = false, precision = 18, scale = 2) private BigDecimal costAmount;
    @Column(name = "receivable_adjustment_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal receivableAdjustmentAmount;
    @Column(name = "refund_amount", nullable = false, precision = 18, scale = 2) private BigDecimal refundAmount;
    @Column(length = 300) private String reason;
    @Column(length = 300) private String remark;
    @Column(name = "posted_by") private UUID postedBy;
    @Column(name = "posted_at") private Instant postedAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected SalesReturn() {
    }

    public static SalesReturn create(String returnNo, UUID customerId, UUID deliveryId, UUID warehouseId,
                                     String reason, String remark, UUID createdBy) {
        SalesReturn value = new SalesReturn();
        value.id = UUID.randomUUID();
        value.returnNo = returnNo;
        value.customerId = customerId;
        value.deliveryId = deliveryId;
        value.warehouseId = warehouseId;
        value.status = SalesReturnStatus.DRAFT;
        value.totalAmount = money(BigDecimal.ZERO);
        value.costAmount = money(BigDecimal.ZERO);
        value.receivableAdjustmentAmount = money(BigDecimal.ZERO);
        value.refundAmount = money(BigDecimal.ZERO);
        value.reason = reason;
        value.remark = remark;
        value.createdBy = createdBy;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void totals(BigDecimal total, BigDecimal cost) {
        this.totalAmount = money(total);
        this.costAmount = money(cost);
        if (totalAmount.signum() <= 0) throw new IllegalArgumentException("退货金额必须大于 0");
    }

    public void post(UUID operatorId, BigDecimal receivableAdjustment, BigDecimal refund) {
        if (status != SalesReturnStatus.DRAFT) throw new IllegalStateException("只有草稿退货单可以过账");
        this.status = SalesReturnStatus.POSTED;
        this.receivableAdjustmentAmount = money(receivableAdjustment);
        this.refundAmount = money(refund);
        this.postedBy = operatorId;
        this.postedAt = Instant.now();
    }

    public void cancel() {
        if (status == SalesReturnStatus.POSTED) throw new IllegalStateException("已过账退货单不能取消");
        this.status = SalesReturnStatus.CANCELLED;
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = SalesReturnStatus.DRAFT;
        if (totalAmount == null) totalAmount = money(BigDecimal.ZERO);
        if (costAmount == null) costAmount = money(BigDecimal.ZERO);
        if (receivableAdjustmentAmount == null) receivableAdjustmentAmount = money(BigDecimal.ZERO);
        if (refundAmount == null) refundAmount = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getReturnNo() { return returnNo; }
    public UUID getCustomerId() { return customerId; }
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getWarehouseId() { return warehouseId; }
    public SalesReturnStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getCostAmount() { return costAmount; }
    public BigDecimal getReceivableAdjustmentAmount() { return receivableAdjustmentAmount; }
    public BigDecimal getRefundAmount() { return refundAmount; }
    public String getReason() { return reason; }
    public String getRemark() { return remark; }
    public UUID getPostedBy() { return postedBy; }
    public Instant getPostedAt() { return postedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
