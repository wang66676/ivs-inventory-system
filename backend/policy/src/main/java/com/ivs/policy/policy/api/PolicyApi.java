package com.ivs.policy.policy.api;

import com.ivs.policy.policy.LimitType;
import com.ivs.policy.policy.PolicyStatus;
import com.ivs.policy.policy.PolicySubsidyPool;
import com.ivs.policy.policy.PreEntitlementStatus;
import com.ivs.policy.policy.PromotionPolicy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 政策与预享受项契约。 */
public final class PolicyApi {

    private PolicyApi() {
    }

    public record PoolView(UUID policyId, LimitType limitType, BigDecimal totalAmount, BigDecimal usedAmount,
                           BigDecimal remainingAmount, BigDecimal perUnitAmount, String conditionDesc,
                           boolean conditionMet, LocalDate effectiveFrom, LocalDate effectiveTo, String status) {
        public static PoolView from(PolicySubsidyPool pool) {
            return new PoolView(pool.getPolicyId(), pool.getLimitType(), pool.getTotalAmount(), pool.getUsedAmount(),
                    pool.getRemainingAmount(), pool.getPerUnitAmount(), pool.getConditionDesc(),
                    pool.isConditionMet(), pool.getEffectiveFrom(), pool.getEffectiveTo(), pool.getStatus());
        }
    }

    public record PolicyView(UUID id, String policyNo, String name, String policyType, UUID categoryId, UUID supplierId, String benefitType,
                             BigDecimal benefitValue, BigDecimal maxAmount, LocalDate effFrom, LocalDate effTo,
                             String referenceDateType, String claimCycle, int claimCutoffDay, int priority,
                             String stackingRule, PolicyStatus status, String remark, PoolView pool,
                             Instant createdAt) {
        public static PolicyView from(PromotionPolicy policy, PolicySubsidyPool pool) {
            return new PolicyView(policy.getId(), policy.getPolicyNo(), policy.getName(), policy.getPolicyType(), policy.getCategoryId(), policy.getSupplierId(),
                    policy.getBenefitType(), policy.getBenefitValue(), policy.getMaxAmount(), policy.getEffFrom(),
                    policy.getEffTo(), policy.getReferenceDateType(), policy.getClaimCycle(),
                    policy.getClaimCutoffDay(), policy.getPriority(), policy.getStackingRule(), policy.getStatus(),
                    policy.getRemark(), pool == null ? null : PoolView.from(pool), policy.getCreatedAt());
        }
    }

    public record SavePolicyCommand(
            String name, String policyType, UUID categoryId, UUID supplierId, List<UUID> productIds, String benefitType,
            BigDecimal benefitValue, BigDecimal maxAmount, LocalDate effFrom, LocalDate effTo,
            String referenceDateType, String claimCycle, Integer claimCutoffDay, Integer priority,
            String stackingRule, LimitType limitType, BigDecimal totalAmount, BigDecimal perUnitAmount,
            String conditionDesc, LocalDate poolEffectiveFrom, LocalDate poolEffectiveTo, String remark
    ) {
    }

    public record PreEntitlementView(
            UUID id, String preNo, UUID orderId, UUID orderLineId, UUID productId, UUID serialId,
            UUID policyId, String policyName, BigDecimal estimateAmount, PreEntitlementStatus status,
            boolean addedAfterOrder, String addReason, String serialNos, String orderNo,
            Instant invoiceIssuedAt, LocalDate retailDate, String referenceDateType,
            LocalDate referenceDate, Instant createdAt
    ) {
    }

    public record ApplyPreEntitlementCommand(
            UUID orderId, UUID orderLineId, UUID productId, UUID serialId, BigDecimal orderLineAmount,
            List<UUID> policyIds, Boolean addedAfterOrder, String reason
    ) {
    }

    public record SyncPreEntitlementLine(
            UUID orderLineId, UUID serialId, UUID productId, BigDecimal orderLineAmount, List<UUID> policyIds
    ) {
    }
}
