package com.ivs.policy.cost;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 单车政策成本分摊：权益生成时按面值落账，厂家确认后按确认额校正。 */
@Entity
@Table(name = "policy_cost_allocation", schema = "ivs")
public class PolicyCostAllocation {

    @Id private UUID id;
    @Column(name = "entitlement_id", nullable = false, unique = true) private UUID entitlementId;
    @Column(name = "order_id") private UUID orderId;
    @Column(name = "retail_id") private UUID retailId;
    @Column(name = "serial_id") private UUID serialId;
    @Column(name = "policy_id", nullable = false) private UUID policyId;
    @Column(name = "cost_bearer", nullable = false, length = 20) private String costBearer;
    @Column(name = "cost_subject", length = 40) private String costSubject;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "period_code", nullable = false, length = 20) private String periodCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected PolicyCostAllocation() {
    }

    public static PolicyCostAllocation create(UUID entitlementId, UUID orderId, UUID retailId, UUID serialId,
                                              UUID policyId, String costBearer, String costSubject,
                                              BigDecimal amount, String periodCode) {
        PolicyCostAllocation row = new PolicyCostAllocation();
        row.id = UUID.randomUUID();
        row.entitlementId = entitlementId;
        row.orderId = orderId;
        row.retailId = retailId;
        row.serialId = serialId;
        row.policyId = policyId;
        row.costBearer = costBearer == null ? "OEM" : costBearer;
        row.costSubject = costSubject;
        row.amount = money(amount);
        row.periodCode = periodCode;
        row.createdAt = Instant.now();
        row.updatedAt = row.createdAt;
        return row;
    }

    public void recognize(BigDecimal confirmedAmount) {
        this.amount = money(confirmedAmount);
        this.updatedAt = Instant.now();
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (amount == null) amount = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getEntitlementId() { return entitlementId; }
    public UUID getOrderId() { return orderId; }
    public UUID getRetailId() { return retailId; }
    public UUID getSerialId() { return serialId; }
    public UUID getPolicyId() { return policyId; }
    public String getCostBearer() { return costBearer; }
    public String getCostSubject() { return costSubject; }
    public BigDecimal getAmount() { return amount; }
    public String getPeriodCode() { return periodCode; }
    public Instant getCreatedAt() { return createdAt; }
}
