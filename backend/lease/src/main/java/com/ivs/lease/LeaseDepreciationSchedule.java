package com.ivs.lease;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 租赁资产月度直线折旧计划。 */
@Entity
@Table(name = "lease_depreciation_schedule", schema = "ivs")
public class LeaseDepreciationSchedule {
    @Id
    private UUID id;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(name = "period_no", nullable = false)
    private int periodNo;
    @Column(name = "depreciation_date", nullable = false)
    private LocalDate depreciationDate;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(name = "accumulated_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal accumulatedAmount;
    @Column(name = "net_value", nullable = false, precision = 18, scale = 2)
    private BigDecimal netValue;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LeaseDepreciationSchedule() {
    }

    public static LeaseDepreciationSchedule create(UUID contractId, int periodNo, LocalDate depreciationDate,
                                                   BigDecimal amount, BigDecimal accumulatedAmount,
                                                   BigDecimal netValue) {
        LeaseDepreciationSchedule schedule = new LeaseDepreciationSchedule();
        schedule.id = UUID.randomUUID();
        schedule.contractId = contractId;
        schedule.periodNo = periodNo;
        schedule.depreciationDate = depreciationDate;
        schedule.amount = amount;
        schedule.accumulatedAmount = accumulatedAmount;
        schedule.netValue = netValue;
        schedule.createdAt = Instant.now();
        return schedule;
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public int getPeriodNo() { return periodNo; }
    public LocalDate getDepreciationDate() { return depreciationDate; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getAccumulatedAmount() { return accumulatedAmount; }
    public BigDecimal getNetValue() { return netValue; }
    public Instant getCreatedAt() { return createdAt; }
}
