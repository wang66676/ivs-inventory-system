package com.ivs.lease;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 租赁提前/到期归还时的押金与租金结算快照。 */
@Entity
@Table(name = "lease_settlement", schema = "ivs")
public class LeaseSettlement {
    @Id
    private UUID id;
    @Column(name = "contract_id", nullable = false, unique = true)
    private UUID contractId;
    @Column(name = "settlement_date", nullable = false)
    private LocalDate settlementDate;
    @Column(name = "outstanding_before", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingBefore;
    @Column(name = "deposit_applied", nullable = false, precision = 18, scale = 2)
    private BigDecimal depositApplied;
    @Column(name = "outstanding_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal outstandingAfter;
    @Column(name = "deposit_refundable", nullable = false, precision = 18, scale = 2)
    private BigDecimal depositRefundable;
    @Column(length = 300)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LeaseSettlement() {
    }

    public static LeaseSettlement create(UUID contractId, LocalDate settlementDate, BigDecimal outstandingBefore,
                                         BigDecimal depositApplied, BigDecimal outstandingAfter,
                                         BigDecimal depositRefundable, String reason) {
        LeaseSettlement settlement = new LeaseSettlement();
        settlement.id = UUID.randomUUID();
        settlement.contractId = contractId;
        settlement.settlementDate = settlementDate;
        settlement.outstandingBefore = outstandingBefore;
        settlement.depositApplied = depositApplied;
        settlement.outstandingAfter = outstandingAfter;
        settlement.depositRefundable = depositRefundable;
        settlement.reason = reason == null || reason.isBlank() ? null : reason.trim();
        settlement.createdAt = Instant.now();
        return settlement;
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public LocalDate getSettlementDate() { return settlementDate; }
    public BigDecimal getOutstandingBefore() { return outstandingBefore; }
    public BigDecimal getDepositApplied() { return depositApplied; }
    public BigDecimal getOutstandingAfter() { return outstandingAfter; }
    public BigDecimal getDepositRefundable() { return depositRefundable; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
