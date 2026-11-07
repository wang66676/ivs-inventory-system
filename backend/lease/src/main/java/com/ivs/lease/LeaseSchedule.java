package com.ivs.lease;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "lease_schedule", schema = "ivs")
public class LeaseSchedule {
    @Id
    private UUID id;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Column(name = "period_no", nullable = false)
    private int periodNo;
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(name = "receivable_id")
    private UUID receivableId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LeaseSchedule() {
    }

    public static LeaseSchedule create(UUID contractId, int periodNo, LocalDate dueDate, BigDecimal amount) {
        LeaseSchedule schedule = new LeaseSchedule();
        schedule.id = UUID.randomUUID();
        schedule.contractId = contractId;
        schedule.periodNo = periodNo;
        schedule.dueDate = dueDate;
        schedule.amount = amount;
        schedule.createdAt = Instant.now();
        return schedule;
    }

    public void bindReceivable(UUID receivableId) {
        this.receivableId = receivableId;
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public int getPeriodNo() { return periodNo; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getAmount() { return amount; }
    public UUID getReceivableId() { return receivableId; }
}
