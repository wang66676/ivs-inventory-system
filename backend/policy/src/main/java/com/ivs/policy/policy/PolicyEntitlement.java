package com.ivs.policy.policy;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 正式权益：实销确认后由预享受项生成，按实销日期归期，进入申报单汇总。 */
@Entity
@Table(name = "policy_entitlement", schema = "ivs")
public class PolicyEntitlement {

    @Id private UUID id;
    @Column(name = "entitlement_no", nullable = false, unique = true, length = 40) private String entitlementNo;
    @Column(name = "pre_entitlement_id") private UUID preEntitlementId;
    @Column(name = "policy_id", nullable = false) private UUID policyId;
    @Column(name = "retail_id") private UUID retailId;
    @Column(name = "supplier_id") private UUID supplierId;
    @Column(name = "serial_id") private UUID serialId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "order_id") private UUID orderId;
    @Column(name = "face_amount", nullable = false, precision = 18, scale = 2) private BigDecimal faceAmount;
    @Column(name = "claimed_amount", nullable = false, precision = 18, scale = 2) private BigDecimal claimedAmount;
    @Column(name = "confirmed_amount", nullable = false, precision = 18, scale = 2) private BigDecimal confirmedAmount;
    @Column(name = "settled_amount", nullable = false, precision = 18, scale = 2) private BigDecimal settledAmount;
    @Column(name = "period_code", nullable = false, length = 20) private String periodCode;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PolicyEntitlement() {
    }

    public static PolicyEntitlement create(String entitlementNo, UUID preId, UUID policyId, UUID supplierId, UUID retailId,
                                           UUID serialId, UUID productId, UUID orderId, BigDecimal faceAmount,
                                           String periodCode) {
        PolicyEntitlement e = new PolicyEntitlement();
        e.id = UUID.randomUUID();
        e.entitlementNo = entitlementNo;
        e.preEntitlementId = preId;
        e.policyId = policyId;
        e.supplierId = supplierId;
        e.retailId = retailId;
        e.serialId = serialId;
        e.productId = productId;
        e.orderId = orderId;
        e.faceAmount = (faceAmount == null ? BigDecimal.ZERO : faceAmount).setScale(2, RoundingMode.HALF_UP);
        e.claimedAmount = BigDecimal.ZERO.setScale(2);
        e.confirmedAmount = BigDecimal.ZERO.setScale(2);
        e.settledAmount = BigDecimal.ZERO.setScale(2);
        e.periodCode = periodCode;
        e.status = "PENDING_CLAIM";
        e.createdAt = Instant.now();
        e.updatedAt = e.createdAt;
        return e;
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = "PENDING_CLAIM";
        if (claimedAmount == null) claimedAmount = BigDecimal.ZERO.setScale(2);
        if (confirmedAmount == null) confirmedAmount = BigDecimal.ZERO.setScale(2);
        if (settledAmount == null) settledAmount = BigDecimal.ZERO.setScale(2);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getEntitlementNo() { return entitlementNo; }
    public UUID getPreEntitlementId() { return preEntitlementId; }
    public UUID getPolicyId() { return policyId; }
    public UUID getRetailId() { return retailId; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getSerialId() { return serialId; }
    public UUID getProductId() { return productId; }
    public UUID getOrderId() { return orderId; }
    public BigDecimal getFaceAmount() { return faceAmount; }
    public BigDecimal getClaimedAmount() { return claimedAmount; }
    public BigDecimal getConfirmedAmount() { return confirmedAmount; }
    public BigDecimal getSettledAmount() { return settledAmount; }
    public String getPeriodCode() { return periodCode; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void markClaimed() {
        if ("PENDING_CLAIM".equals(status)) {
            this.claimedAmount = faceAmount;
            status = "CLAIMED";
        }
    }

    public void markSubmitted() {
        if ("CLAIMED".equals(status)) status = "SUBMITTED";
    }

    public void confirm(BigDecimal amount, boolean disputed) {
        this.confirmedAmount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        if (disputed) {
            this.status = "DISPUTED";
        } else if (confirmedAmount.compareTo(faceAmount) >= 0) {
            this.status = "CONFIRMED";
        } else {
            this.status = "DIFFERENCE";
        }
    }

    public void settle(BigDecimal amount) {
        this.settledAmount = (amount == null ? confirmedAmount : amount).setScale(2, RoundingMode.HALF_UP);
        this.status = "SETTLED";
    }

    public long getVersion() { return version; }
}



