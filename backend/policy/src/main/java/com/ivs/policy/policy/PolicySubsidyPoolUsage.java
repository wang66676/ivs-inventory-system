package com.ivs.policy.policy;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 政策额度池逐台扣减流水（取消时写负数行）。 */
@Entity
@Table(name = "policy_subsidy_pool_usage", schema = "ivs")
public class PolicySubsidyPoolUsage {

    @Id private UUID id;
    @Column(name = "pool_id", nullable = false) private UUID poolId;
    @Column(name = "source_type", nullable = false, length = 30) private String sourceType;
    @Column(name = "source_id") private UUID sourceId;
    @Column(name = "source_no", length = 60) private String sourceNo;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "used_at", nullable = false) private Instant usedAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PolicySubsidyPoolUsage() {
    }

    public static PolicySubsidyPoolUsage create(UUID poolId, String sourceType, UUID sourceId, String sourceNo,
                                                BigDecimal amount) {
        PolicySubsidyPoolUsage usage = new PolicySubsidyPoolUsage();
        usage.id = UUID.randomUUID();
        usage.poolId = poolId;
        usage.sourceType = sourceType;
        usage.sourceId = sourceId;
        usage.sourceNo = sourceNo;
        usage.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        usage.usedAt = Instant.now();
        usage.createdAt = usage.usedAt;
        return usage;
    }

    public UUID getId() { return id; }
    public UUID getPoolId() { return poolId; }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getSourceNo() { return sourceNo; }
    public BigDecimal getAmount() { return amount; }
    public Instant getUsedAt() { return usedAt; }
}
