package com.ivs.policy.policy;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 优惠政策：人工录入，按车型多选、金额或比例、生效/失效、限制维度与申报周期。 */
@Entity
@Table(name = "promotion_policy", schema = "ivs")
public class PromotionPolicy {

    @Id private UUID id;
    @Column(name = "policy_no", nullable = false, unique = true, length = 40) private String policyNo;
    @Column(nullable = false, length = 150) private String name;
    @Column(name = "policy_type", nullable = false, length = 30) private String policyType;
    @Column(name = "category_id") private UUID categoryId;
    @Column(name = "supplier_id") private UUID supplierId;
    @Column(name = "model_list", columnDefinition = "text") private String modelList;
    @Column(name = "benefit_type", nullable = false, length = 20) private String benefitType;
    @Column(name = "benefit_value", nullable = false, precision = 18, scale = 4) private BigDecimal benefitValue;
    @Column(name = "max_amount", precision = 18, scale = 2) private BigDecimal maxAmount;
    @Column(name = "eff_from", nullable = false) private LocalDate effFrom;
    @Column(name = "eff_to", nullable = false) private LocalDate effTo;
    @Column(name = "reference_date_type", nullable = false, length = 20) private String referenceDateType;
    @Column(name = "claim_cycle", nullable = false, length = 20) private String claimCycle;
    @Column(name = "claim_cutoff_day", nullable = false) private int claimCutoffDay;
    @Column(nullable = false) private int priority;
    @Column(name = "stacking_rule", nullable = false, length = 20) private String stackingRule;
    @Column(name = "cost_bearer", nullable = false, length = 20) private String costBearer;
    @Column(name = "cost_subject", length = 40) private String costSubject;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private PolicyStatus status;
    @Column(length = 300) private String remark;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PromotionPolicy() {
    }

    public static PromotionPolicy create(String policyNo, String name, String policyType, UUID categoryId, UUID supplierId,
                                         String modelList, String benefitType, BigDecimal benefitValue,
                                         BigDecimal maxAmount, LocalDate effFrom, LocalDate effTo,
                                         String referenceDateType, String claimCycle, int claimCutoffDay,
                                         int priority, String stackingRule, String costBearer,
                                         String costSubject, String remark) {
        PromotionPolicy policy = new PromotionPolicy();
        policy.id = UUID.randomUUID();
        policy.policyNo = policyNo;
        policy.name = name;
        policy.policyType = policyType;
        policy.categoryId = categoryId;
        policy.supplierId = supplierId;
        policy.modelList = modelList;
        policy.benefitType = benefitType;
        policy.benefitValue = benefitValue;
        policy.maxAmount = maxAmount;
        policy.effFrom = effFrom;
        policy.effTo = effTo;
        policy.referenceDateType = referenceDateType;
        policy.claimCycle = claimCycle;
        policy.claimCutoffDay = claimCutoffDay;
        policy.priority = priority;
        policy.stackingRule = stackingRule;
        policy.costBearer = costBearer;
        policy.costSubject = costSubject;
        policy.status = PolicyStatus.DRAFT;
        policy.remark = remark;
        policy.createdAt = Instant.now();
        policy.updatedAt = policy.createdAt;
        return policy;
    }

    public void publish() {
        if (status == PolicyStatus.PUBLISHED) return;
        if (status != PolicyStatus.DRAFT) throw new IllegalStateException("只有草稿政策可以发布");
        if (effTo.isBefore(effFrom)) throw new IllegalStateException("失效日期不能早于生效日期");
        status = PolicyStatus.PUBLISHED;
    }

    public void cancel() {
        status = PolicyStatus.CANCELLED;
    }

    public boolean activeOn(LocalDate date) {
        return status == PolicyStatus.PUBLISHED && !date.isBefore(effFrom) && !date.isAfter(effTo);
    }

    /** 本次可享受金额：定额直接取，比例按订单行金额计算，受 max_amount 封顶。 */
    public BigDecimal benefitOf(BigDecimal orderLineAmount) {
        BigDecimal amount = "RATE".equals(benefitType) && orderLineAmount != null
                ? orderLineAmount.multiply(benefitValue)
                : benefitValue;
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (maxAmount != null && amount.compareTo(maxAmount) > 0) {
            amount = maxAmount.setScale(2, RoundingMode.HALF_UP);
        }
        return amount;
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = PolicyStatus.DRAFT;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public String getPolicyNo() { return policyNo; }
    public String getName() { return name; }
    public String getPolicyType() { return policyType; }
    public UUID getCategoryId() { return categoryId; }
    public UUID getSupplierId() { return supplierId; }
    public String getModelList() { return modelList; }
    public String getBenefitType() { return benefitType; }
    public BigDecimal getBenefitValue() { return benefitValue; }
    public BigDecimal getMaxAmount() { return maxAmount; }
    public LocalDate getEffFrom() { return effFrom; }
    public LocalDate getEffTo() { return effTo; }
    public String getReferenceDateType() { return referenceDateType; }
    public String getClaimCycle() { return claimCycle; }
    public int getClaimCutoffDay() { return claimCutoffDay; }
    public int getPriority() { return priority; }
    public String getStackingRule() { return stackingRule; }
    public String getCostBearer() { return costBearer; }
    public String getCostSubject() { return costSubject; }
    public PolicyStatus getStatus() { return status; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}

