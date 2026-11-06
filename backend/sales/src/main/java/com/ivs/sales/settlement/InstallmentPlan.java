package com.ivs.sales.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 分期收款计划：首付 + 分期应收，全部结清后完成以租代售所有权转移。 */
@Entity
@Table(name = "installment_plan", schema = "ivs")
public class InstallmentPlan {

    @Id
    private UUID id;

    @Column(name = "plan_no", nullable = false, unique = true, length = 40)
    private String planNo;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "down_payment", nullable = false, precision = 18, scale = 2)
    private BigDecimal downPayment;

    @Column(name = "financed_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal financedAmount;

    @Column(name = "installment_count", nullable = false)
    private int installmentCount;

    @Column(name = "first_due_date", nullable = false)
    private LocalDate firstDueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstallmentPlanStatus status;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected InstallmentPlan() {
    }

    public static InstallmentPlan create(String planNo, UUID orderId, UUID customerId, BigDecimal totalAmount,
                                         BigDecimal downPayment, int installmentCount, LocalDate firstDueDate) {
        InstallmentPlan plan = new InstallmentPlan();
        plan.id = UUID.randomUUID();
        plan.planNo = planNo;
        plan.orderId = orderId;
        plan.customerId = customerId;
        plan.totalAmount = money(totalAmount);
        plan.downPayment = money(downPayment);
        plan.financedAmount = money(plan.totalAmount.subtract(plan.downPayment));
        plan.installmentCount = installmentCount;
        plan.firstDueDate = firstDueDate;
        plan.status = InstallmentPlanStatus.ACTIVE;
        plan.createdAt = Instant.now();
        plan.updatedAt = plan.createdAt;
        if (plan.downPayment.signum() < 0 || plan.financedAmount.signum() <= 0) {
            throw new IllegalArgumentException("首付必须小于订单总额，且分期金额必须大于 0");
        }
        if (installmentCount <= 0 || installmentCount > 60) {
            throw new IllegalArgumentException("分期期数必须在 1 到 60 之间");
        }
        if (firstDueDate == null) {
            throw new IllegalArgumentException("首期到期日不能为空");
        }
        return plan;
    }

    public void settle() {
        if (status == InstallmentPlanStatus.SETTLED) return;
        status = InstallmentPlanStatus.SETTLED;
        settledAt = Instant.now();
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = InstallmentPlanStatus.ACTIVE;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getPlanNo() { return planNo; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getDownPayment() { return downPayment; }
    public BigDecimal getFinancedAmount() { return financedAmount; }
    public int getInstallmentCount() { return installmentCount; }
    public LocalDate getFirstDueDate() { return firstDueDate; }
    public InstallmentPlanStatus getStatus() { return status; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getCreatedAt() { return createdAt; }
}
