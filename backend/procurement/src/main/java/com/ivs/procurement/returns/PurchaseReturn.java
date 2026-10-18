package com.ivs.procurement.returns;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 采购退货单：草稿 → 已过账；过账时库存减少并冲减采购金额。 */
@Entity
@Table(name = "purchase_return", schema = "ivs")
public class PurchaseReturn {

    @Id private UUID id;
    @Column(name = "return_no", nullable = false, unique = true, length = 40) private String returnNo;
    @Column(name = "supplier_id", nullable = false) private UUID supplierId;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PurchaseReturnStatus status;
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2) private BigDecimal totalAmount;
    @Column(length = 300) private String reason;
    @Column(length = 300) private String remark;
    @Column(name = "posted_by") private UUID postedBy;
    @Column(name = "posted_at") private Instant postedAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PurchaseReturn() {
    }

    public static PurchaseReturn create(String returnNo, UUID supplierId, UUID warehouseId,
                                        String reason, String remark, UUID createdBy) {
        PurchaseReturn value = new PurchaseReturn();
        value.id = UUID.randomUUID();
        value.returnNo = returnNo;
        value.supplierId = supplierId;
        value.warehouseId = warehouseId;
        value.status = PurchaseReturnStatus.DRAFT;
        value.totalAmount = money(BigDecimal.ZERO);
        value.reason = reason;
        value.remark = remark;
        value.createdBy = createdBy;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void total(BigDecimal amount) {
        this.totalAmount = money(amount);
        if (this.totalAmount.signum() <= 0) throw new IllegalArgumentException("退货金额必须大于 0");
    }

    public void post(UUID operatorId) {
        if (status != PurchaseReturnStatus.DRAFT) throw new IllegalStateException("只有草稿退货单可以过账");
        this.status = PurchaseReturnStatus.POSTED;
        this.postedBy = operatorId;
        this.postedAt = Instant.now();
    }

    public void cancel() {
        if (status == PurchaseReturnStatus.POSTED) throw new IllegalStateException("已过账退货单不能取消");
        this.status = PurchaseReturnStatus.CANCELLED;
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = PurchaseReturnStatus.DRAFT;
        if (totalAmount == null) totalAmount = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getReturnNo() { return returnNo; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getWarehouseId() { return warehouseId; }
    public PurchaseReturnStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getReason() { return reason; }
    public String getRemark() { return remark; }
    public UUID getPostedBy() { return postedBy; }
    public Instant getPostedAt() { return postedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
