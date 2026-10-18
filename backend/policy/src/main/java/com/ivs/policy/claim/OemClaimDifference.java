package com.ivs.policy.claim;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 申报差异：确认额小于申报额时强制登记原因与处置，支持后续标记已解决。 */
@Entity
@Table(name = "oem_claim_difference", schema = "ivs")
public class OemClaimDifference {

    @Id private UUID id;
    @Column(name = "claim_id", nullable = false) private UUID claimId;
    @Column(name = "claim_line_id", nullable = false) private UUID claimLineId;
    @Column(name = "entitlement_id", nullable = false) private UUID entitlementId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 300) private String reason;
    @Column(nullable = false, length = 30) private String disposition;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "resolution_remark", length = 300) private String resolutionRemark;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "resolved_at") private Instant resolvedAt;

    protected OemClaimDifference() {
    }

    public static OemClaimDifference create(UUID claimId, UUID claimLineId, UUID entitlementId,
                                            BigDecimal amount, String reason, String disposition) {
        OemClaimDifference diff = new OemClaimDifference();
        diff.id = UUID.randomUUID();
        diff.claimId = claimId;
        diff.claimLineId = claimLineId;
        diff.entitlementId = entitlementId;
        diff.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        diff.reason = reason;
        diff.disposition = disposition;
        diff.status = "OPEN";
        diff.createdAt = Instant.now();
        return diff;
    }

    public void resolve(String remark) {
        this.status = "RESOLVED";
        this.resolutionRemark = remark;
        this.resolvedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getClaimId() { return claimId; }
    public UUID getClaimLineId() { return claimLineId; }
    public UUID getEntitlementId() { return entitlementId; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public String getDisposition() { return disposition; }
    public String getStatus() { return status; }
    public String getResolutionRemark() { return resolutionRemark; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
