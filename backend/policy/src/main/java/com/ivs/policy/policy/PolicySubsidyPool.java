package com.ivs.policy.policy;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 政策额度池：每条政策一个。
 * 时间限制只看有效期；金额限制逐台扣减、返完为止；条件限制需人工标记条件达成。
 */
@Entity
@Table(name = "policy_subsidy_pool", schema = "ivs")
public class PolicySubsidyPool {

    @Id private UUID id;
    @Column(name = "policy_id", nullable = false, unique = true) private UUID policyId;
    @Enumerated(EnumType.STRING) @Column(name = "limit_type", nullable = false, length = 30) private LimitType limitType;
    @Column(name = "total_amount", precision = 18, scale = 2) private BigDecimal totalAmount;
    @Column(name = "used_amount", nullable = false, precision = 18, scale = 2) private BigDecimal usedAmount;
    @Column(name = "remaining_amount", precision = 18, scale = 2) private BigDecimal remainingAmount;
    @Column(name = "per_unit_amount", precision = 18, scale = 2) private BigDecimal perUnitAmount;
    @Column(name = "condition_desc", length = 300) private String conditionDesc;
    @Column(name = "condition_met", nullable = false) private boolean conditionMet;
    @Column(name = "effective_from") private LocalDate effectiveFrom;
    @Column(name = "effective_to") private LocalDate effectiveTo;
    @Column(name = "exhaust_rule", nullable = false, length = 20) private String exhaustRule;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PolicySubsidyPool() {
    }

    public static PolicySubsidyPool create(UUID policyId, LimitType limitType, BigDecimal totalAmount,
                                           BigDecimal perUnitAmount, String conditionDesc,
                                           LocalDate effectiveFrom, LocalDate effectiveTo) {
        PolicySubsidyPool pool = new PolicySubsidyPool();
        pool.id = UUID.randomUUID();
        pool.policyId = policyId;
        pool.limitType = limitType;
        pool.totalAmount = money(totalAmount);
        pool.remainingAmount = pool.totalAmount;
        pool.usedAmount = money(BigDecimal.ZERO);
        pool.perUnitAmount = money(perUnitAmount);
        pool.conditionDesc = conditionDesc;
        pool.conditionMet = false;
        pool.effectiveFrom = effectiveFrom;
        pool.effectiveTo = effectiveTo;
        pool.exhaustRule = "PARTIAL";
        pool.status = "ACTIVE";
        pool.createdAt = Instant.now();
        pool.updatedAt = pool.createdAt;
        return pool;
    }

    /**
     * 预占额度（订单选政策时）：金额限制时原子扣减；返完为止。
     *
     * @return 实际可享受金额（0 表示额度已用尽）
     */
    public BigDecimal reserve(BigDecimal requested) {
        return reserve(requested, true);
    }

    /**
     * 采购单选择政策时允许先形成预享受项：金额仍正常预占，但条件类政策不因尚未人工确认而阻断采购录单。
     */
    public BigDecimal reserveProvisional(BigDecimal requested) {
        return reserve(requested, false);
    }

    private BigDecimal reserve(BigDecimal requested, boolean requireConditionMet) {
        BigDecimal want = money(requested);
        if (limitType == LimitType.TIME_ONLY) {
            return want;
        }
        if (limitType == LimitType.CONDITION || limitType == LimitType.AMOUNT_AND_CONDITION) {
            if (requireConditionMet && !conditionMet) {
                throw new IllegalStateException("政策条件尚未达成，不能享受：" +
                        (conditionDesc == null ? "" : conditionDesc));
            }
            if (limitType == LimitType.CONDITION) {
                return want;
            }
        }
        BigDecimal remaining = remainingAmount == null ? BigDecimal.ZERO : remainingAmount;
        BigDecimal applied = remaining.min(want);
        if (applied.signum() <= 0) {
            return money(BigDecimal.ZERO);
        }
        this.remainingAmount = money(remaining.subtract(applied));
        this.usedAmount = money(usedAmount.add(applied));
        if (this.remainingAmount.signum() == 0) {
            this.status = "EXHAUSTED";
        }
        return applied;
    }

    /** 取消预享受时释放额度（仅金额类限制需要回补；时间/条件类不扣金额）。 */
    public void release(BigDecimal amount) {
        if (!amountLimited()) {
            return;
        }
        BigDecimal value = money(amount);
        this.remainingAmount = money((remainingAmount == null ? BigDecimal.ZERO : remainingAmount).add(value));
        this.usedAmount = money(usedAmount.subtract(value).max(BigDecimal.ZERO));
        if (this.remainingAmount.signum() > 0 && "EXHAUSTED".equals(status)) {
            this.status = "ACTIVE";
        }
    }

    public void markConditionMet() {
        this.conditionMet = true;
    }

    /** 审批通过后调整额度池总额，剩余额度按已用金额重新计算。 */
    public void adjustTotalAmount(BigDecimal newTotalAmount) {
        if (!amountLimited()) {
            throw new IllegalStateException("只有金额类额度池可以调整总额");
        }
        BigDecimal target = money(newTotalAmount);
        if (target == null || target.signum() < 0) {
            throw new IllegalArgumentException("额度池总额不能小于 0");
        }
        BigDecimal used = usedAmount == null ? BigDecimal.ZERO : usedAmount;
        if (target.compareTo(used) < 0) {
            throw new IllegalArgumentException("额度池总额不能小于已使用金额");
        }
        this.totalAmount = target;
        this.remainingAmount = money(target.subtract(used));
        this.status = this.remainingAmount.signum() == 0 ? "EXHAUSTED" : "ACTIVE";
    }

    public boolean amountLimited() {
        return limitType == LimitType.AMOUNT || limitType == LimitType.AMOUNT_AND_CONDITION;
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = "ACTIVE";
        if (exhaustRule == null) exhaustRule = "PARTIAL";
        if (usedAmount == null) usedAmount = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getPolicyId() { return policyId; }
    public LimitType getLimitType() { return limitType; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getUsedAmount() { return usedAmount; }
    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public BigDecimal getPerUnitAmount() { return perUnitAmount; }
    public String getConditionDesc() { return conditionDesc; }
    public boolean isConditionMet() { return conditionMet; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public String getStatus() { return status; }
    public long getVersion() { return version; }
}

