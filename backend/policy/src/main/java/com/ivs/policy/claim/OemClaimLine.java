package com.ivs.policy.claim;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 申报单明细：一条正式权益一行，保留政策维度用于厂家逐行确认。 */
@Entity
@Table(name = "oem_claim_line", schema = "ivs")
public class OemClaimLine {

    @Id private UUID id;
    @Column(name = "claim_id", nullable = false) private UUID claimId;
    @Column(name = "entitlement_id", nullable = false, unique = true) private UUID entitlementId;
    @Column(name = "retail_id") private UUID retailId;
    @Column(name = "policy_id") private UUID policyId;
    @Column(name = "serial_id") private UUID serialId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "confirmed_amount", nullable = false, precision = 18, scale = 2) private BigDecimal confirmedAmount;
    @Column(name = "difference_amount", nullable = false, precision = 18, scale = 2) private BigDecimal differenceAmount;
    @Column(name = "difference_reason", length = 300) private String differenceReason;
    @Column(name = "difference_disposition", length = 30) private String differenceDisposition;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    protected OemClaimLine() {
    }

    public static OemClaimLine create(UUID claimId, UUID entitlementId, UUID retailId, UUID policyId,
                                      UUID serialId, BigDecimal amount) {
        OemClaimLine line = new OemClaimLine();
        line.id = UUID.randomUUID();
        line.claimId = claimId;
        line.entitlementId = entitlementId;
        line.retailId = retailId;
        line.policyId = policyId;
        line.serialId = serialId;
        line.amount = money(amount);
        line.confirmedAmount = money(BigDecimal.ZERO);
        line.differenceAmount = money(BigDecimal.ZERO);
        line.createdAt = Instant.now();
        line.updatedAt = line.createdAt;
        return line;
    }

    public void confirm(BigDecimal confirmed, String reason, String disposition) {
        BigDecimal value = money(confirmed);
        if (value.signum() < 0 || value.compareTo(amount) > 0) {
            throw new IllegalArgumentException("明细确认额不能为负且不能大于申报金额");
        }
        this.confirmedAmount = value;
        this.differenceAmount = money(amount.subtract(value));
        if (this.differenceAmount.signum() > 0) {
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("存在申报差异时必须填写原因");
            if (disposition == null || disposition.isBlank()) throw new IllegalArgumentException("存在申报差异时必须选择处置方式");
            this.differenceReason = reason.trim();
            this.differenceDisposition = disposition.trim();
        }
        this.updatedAt = Instant.now();
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (amount == null) amount = money(BigDecimal.ZERO);
        if (confirmedAmount == null) confirmedAmount = money(BigDecimal.ZERO);
        if (differenceAmount == null) differenceAmount = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public UUID getEntitlementId() { return entitlementId; }
    public UUID getRetailId() { return retailId; }
    public UUID getPolicyId() { return policyId; }
    public UUID getSerialId() { return serialId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getConfirmedAmount() { return confirmedAmount; }
    public BigDecimal getDifferenceAmount() { return differenceAmount; }
    public String getDifferenceReason() { return differenceReason; }
    public String getDifferenceDisposition() { return differenceDisposition; }
    public Instant getCreatedAt() { return createdAt; }
}
