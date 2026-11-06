package com.ivs.sales.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 分期期次：关联一笔应收，实际收款与逾期由应收模块统一处理。 */
@Entity
@Table(name = "installment_schedule", schema = "ivs")
public class InstallmentSchedule {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "period_no", nullable = false)
    private int periodNo;

    @Column(name = "receivable_id", nullable = false, unique = true)
    private UUID receivableId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InstallmentSchedule() {
    }

    public static InstallmentSchedule create(UUID planId, int periodNo, UUID receivableId,
                                             LocalDate dueDate, BigDecimal amount) {
        InstallmentSchedule schedule = new InstallmentSchedule();
        schedule.id = UUID.randomUUID();
        schedule.planId = planId;
        schedule.periodNo = periodNo;
        schedule.receivableId = receivableId;
        schedule.dueDate = dueDate;
        schedule.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        schedule.createdAt = Instant.now();
        return schedule;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPlanId() { return planId; }
    public int getPeriodNo() { return periodNo; }
    public UUID getReceivableId() { return receivableId; }
    public LocalDate getDueDate() { return dueDate; }
    public BigDecimal getAmount() { return amount; }
}
