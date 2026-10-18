package com.ivs.policy.policy;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 预享受项：订单选政策生成，实销前只是预占；可取消并释放额度。 */
@Entity
@Table(name = "policy_pre_entitlement", schema = "ivs")
public class PolicyPreEntitlement {

    @Id private UUID id;
    @Column(name = "pre_no", nullable = false, unique = true, length = 40) private String preNo;
    @Column(name = "order_id", nullable = false) private UUID orderId;
    @Column(name = "order_line_id") private UUID orderLineId;
    @Column(name = "serial_id") private UUID serialId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "policy_id", nullable = false) private UUID policyId;
    @Column(name = "estimate_amount", nullable = false, precision = 18, scale = 2) private BigDecimal estimateAmount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PreEntitlementStatus status;
    @Column(name = "added_after_order", nullable = false) private boolean addedAfterOrder;
    @Column(name = "added_by") private UUID addedBy;
    @Column(name = "added_at") private Instant addedAt;
    @Column(name = "add_reason", length = 300) private String addReason;
    @Column(length = 300) private String remark;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PolicyPreEntitlement() {
    }

    public static PolicyPreEntitlement create(String preNo, UUID orderId, UUID orderLineId, UUID serialId,
                                              UUID productId, UUID policyId, BigDecimal estimateAmount,
                                              boolean addedAfterOrder, UUID addedBy, String addReason,
                                              String remark) {
        PolicyPreEntitlement pre = new PolicyPreEntitlement();
        pre.id = UUID.randomUUID();
        pre.preNo = preNo;
        pre.orderId = orderId;
        pre.orderLineId = orderLineId;
        pre.serialId = serialId;
        pre.productId = productId;
        pre.policyId = policyId;
        pre.estimateAmount = estimateAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        pre.status = PreEntitlementStatus.PENDING_RETAIL;
        pre.addedAfterOrder = addedAfterOrder;
        pre.addedBy = addedBy;
        pre.addedAt = Instant.now();
        pre.addReason = addReason;
        pre.remark = remark;
        pre.createdAt = pre.addedAt;
        pre.updatedAt = pre.createdAt;
        return pre;
    }

    /** 实销确认后转为可申请状态，防止重复生成权益。 */
    public void markReady() {
        if (status == PreEntitlementStatus.PENDING_RETAIL) {
            status = PreEntitlementStatus.READY;
        }
    }

    public void markApplied() {
        if (status == PreEntitlementStatus.READY) status = PreEntitlementStatus.APPLIED;
    }

    public void markSettled() {
        if (status == PreEntitlementStatus.APPLIED) status = PreEntitlementStatus.SETTLED;
    }

    public void cancel() {
        if (status == PreEntitlementStatus.CANCELLED) return;
        if (status == PreEntitlementStatus.SETTLED) {
            throw new IllegalStateException("已兑现的预享受项不能取消");
        }
        status = PreEntitlementStatus.CANCELLED;
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = PreEntitlementStatus.PENDING_RETAIL;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getPreNo() { return preNo; }
    public UUID getOrderId() { return orderId; }
    public UUID getOrderLineId() { return orderLineId; }
    public UUID getSerialId() { return serialId; }
    public UUID getProductId() { return productId; }
    public UUID getPolicyId() { return policyId; }
    public BigDecimal getEstimateAmount() { return estimateAmount; }
    public PreEntitlementStatus getStatus() { return status; }
    public boolean isAddedAfterOrder() { return addedAfterOrder; }
    public String getAddReason() { return addReason; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
