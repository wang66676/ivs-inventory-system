package com.ivs.lease;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 租赁合同续租/变更历史。 */
@Entity
@Table(name = "lease_contract_change", schema = "ivs")
public class LeaseContractChange {
    @Id
    private UUID id;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(name = "change_type", nullable = false, length = 20)
    private String changeType;
    @Column(name = "old_end_date", nullable = false)
    private LocalDate oldEndDate;
    @Column(name = "new_end_date", nullable = false)
    private LocalDate newEndDate;
    @Column(name = "old_monthly_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal oldMonthlyFee;
    @Column(name = "new_monthly_fee", nullable = false, precision = 18, scale = 2)
    private BigDecimal newMonthlyFee;
    @Column(length = 300)
    private String reason;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LeaseContractChange() {
    }

    public static LeaseContractChange renew(UUID contractId, LocalDate oldEndDate, LocalDate newEndDate,
                                            BigDecimal oldMonthlyFee, BigDecimal newMonthlyFee, String reason) {
        LeaseContractChange change = new LeaseContractChange();
        change.id = UUID.randomUUID();
        change.contractId = contractId;
        change.changeType = "RENEW";
        change.oldEndDate = oldEndDate;
        change.newEndDate = newEndDate;
        change.oldMonthlyFee = oldMonthlyFee;
        change.newMonthlyFee = newMonthlyFee;
        change.reason = reason == null || reason.isBlank() ? null : reason.trim();
        change.createdAt = Instant.now();
        return change;
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public String getChangeType() { return changeType; }
    public LocalDate getOldEndDate() { return oldEndDate; }
    public LocalDate getNewEndDate() { return newEndDate; }
    public BigDecimal getOldMonthlyFee() { return oldMonthlyFee; }
    public BigDecimal getNewMonthlyFee() { return newMonthlyFee; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
}
