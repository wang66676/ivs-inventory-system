package com.ivs.policy.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.policy.cost.PolicyCostAllocation;
import com.ivs.policy.cost.PolicyCostAllocationRepository;
import com.ivs.policy.policy.api.EntitlementApi;
import com.ivs.policy.policy.api.PolicyApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.event.EventEnvelope;
import com.ivs.shared.event.EventPublisher;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 政策引擎：政策录入与发布、额度池（时间/金额/条件）、订单选政策（预享受项）。
 *
 * <p>预享受项在「订单选政策」时即预占额度，取消时释放；实销确认后再转正式权益（下一块）。</p>
 */
@Service
public class PolicyApplicationService {

    private final PromotionPolicyRepository policyRepository;
    private final PolicySubsidyPoolRepository poolRepository;
    private final PolicyPreEntitlementRepository preRepository;
    private final PolicyEntitlementRepository entitlementRepository;
    private final PolicyCostAllocationRepository costAllocationRepository;
    private final PolicySubsidyPoolUsageRepository poolUsageRepository;
    private final EventPublisher events;
    private final CurrentActorProvider actors;
    private final ObjectMapper objectMapper;

    public PolicyApplicationService(PromotionPolicyRepository policyRepository,
                                    PolicySubsidyPoolRepository poolRepository,
                                    PolicyPreEntitlementRepository preRepository,
                                    PolicyEntitlementRepository entitlementRepository,
                                    PolicyCostAllocationRepository costAllocationRepository,
                                    PolicySubsidyPoolUsageRepository poolUsageRepository,
                                    EventPublisher events,
                                    CurrentActorProvider actors,
                                    ObjectMapper objectMapper) {
        this.policyRepository = policyRepository;
        this.poolRepository = poolRepository;
        this.preRepository = preRepository;
        this.entitlementRepository = entitlementRepository;
        this.costAllocationRepository = costAllocationRepository;
        this.poolUsageRepository = poolUsageRepository;
        this.events = events;
        this.actors = actors;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PolicyApi.PolicyView> list(PolicyStatus status) {
        List<PromotionPolicy> policies = status == null ? policyRepository.findAllByOrderByPriorityDescCreatedAtDesc()
                : policyRepository.findByStatusOrderByPriorityDescCreatedAtDesc(status);
        return policies.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public PolicyApi.PolicyView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public PolicyApi.PolicyView create(PolicyApi.SavePolicyCommand command) {
        if (command.effFrom() == null || command.effTo() == null) {
            throw new ConflictException("POLICY_EFFECTIVE_REQUIRED", "请填写政策生效与失效日期");
        }
        if (command.effTo().isBefore(command.effFrom())) {
            throw new ConflictException("POLICY_EFFECTIVE_INVALID", "失效日期不能早于生效日期");
        }
        String policyNo = "POL-%06d".formatted(policyRepository.nextPolicySequence());
        PromotionPolicy policy = PromotionPolicy.create(policyNo, requireText(command.name(), "政策名称不能为空"),
                defaultValue(command.policyType(), "PHASE_SUBSIDY"), command.categoryId(), command.supplierId(),
                productIdsJson(command.productIds()), defaultValue(command.benefitType(), "AMOUNT"),
                requireAmount(command.benefitValue(), "政策金额/比例不能为空"), command.maxAmount(),
                command.effFrom(), command.effTo(), defaultValue(command.referenceDateType(), "RETAIL_DATE"),
                defaultValue(command.claimCycle(), "MONTHLY"),
                command.claimCutoffDay() == null ? 5 : command.claimCutoffDay(),
                command.priority() == null ? 0 : command.priority(),
                defaultValue(command.stackingRule(), "ADDITIVE"), "OEM", "PURCHASE_COST", command.remark());
        PromotionPolicy saved = policyRepository.save(policy);
        LimitType limitType = command.limitType() == null ? LimitType.AMOUNT : command.limitType();
        if (limitType == LimitType.AMOUNT || limitType == LimitType.AMOUNT_AND_CONDITION) {
            if (command.totalAmount() == null || command.totalAmount().signum() <= 0) {
                throw new ConflictException("POOL_TOTAL_REQUIRED", "金额限制的政策必须填写额度池总额");
            }
        }
        PolicySubsidyPool pool = PolicySubsidyPool.create(saved.getId(), limitType, command.totalAmount(),
                command.perUnitAmount(), command.conditionDesc(), command.poolEffectiveFrom(),
                command.poolEffectiveTo());
        poolRepository.save(pool);
        return PolicyApi.PolicyView.from(saved, pool);
    }

    @Transactional
    public PolicyApi.PolicyView publish(UUID id) {
        PromotionPolicy policy = find(id);
        try {
            policy.publish();
        } catch (IllegalStateException exception) {
            throw new ConflictException("POLICY_STATUS_INVALID", exception.getMessage());
        }
        return PolicyApi.PolicyView.from(policyRepository.save(policy), poolOf(id));
    }

    @Transactional
    public PolicyApi.PolicyView cancel(UUID id) {
        PromotionPolicy policy = find(id);
        policy.cancel();
        return PolicyApi.PolicyView.from(policyRepository.save(policy), poolOf(id));
    }

    /** 条件类政策：业务人工标记「条件已达成」后，预享受才能通过。 */
    @Transactional
    public PolicyApi.PolicyView markConditionMet(UUID id) {
        PolicySubsidyPool pool = poolOf(id);
        if (pool == null) {
            throw new NotFoundException("政策额度池", id);
        }
        pool.markConditionMet();
        poolRepository.save(pool);
        return toView(find(id));
    }

    /** 订单选政策：一条政策生成一条预享受项，金额累加；额度池在此预占。 */
    @Transactional
    public List<PolicyApi.PreEntitlementView> apply(PolicyApi.ApplyPreEntitlementCommand command) {
        if (command.policyIds() == null || command.policyIds().isEmpty()) {
            throw new ConflictException("POLICY_SELECTION_REQUIRED", "请至少选择一条政策");
        }
        LocalDate today = LocalDate.now();
        List<PromotionPolicy> candidates = new ArrayList<>();
        for (UUID policyId : command.policyIds()) {
            PromotionPolicy policy = find(policyId);
            if (!policy.activeOn(today)) {
                continue;
            }
            if (!matchesProduct(policy, command.productId())) {
                continue;
            }
            candidates.add(policy);
        }
        if (candidates.isEmpty()) {
            throw new ConflictException("NO_POLICY_AVAILABLE", "所选政策均不在有效期内或不适用于该商品");
        }
        candidates.sort(Comparator.comparingInt(PromotionPolicy::getPriority).reversed());
        if (candidates.stream().anyMatch(policy -> !"ADDITIVE".equals(policy.getStackingRule()))) {
            candidates = List.of(candidates.get(0));
        }
        List<PolicyApi.PreEntitlementView> created = new ArrayList<>();
        for (PromotionPolicy policy : candidates) {
            if (preRepository.findByOrderIdAndOrderLineIdAndPolicyId(command.orderId(), command.orderLineId(),
                    policy.getId()).isPresent()) {
                throw new ConflictException("PRE_DUPLICATE", "同一订单行已享受政策：" + policy.getName());
            }
            BigDecimal requested = policy.benefitOf(command.orderLineAmount());
            PolicySubsidyPool pool = poolRepository.findByPolicyIdForUpdate(policy.getId())
                    .orElseThrow(() -> new NotFoundException("政策额度池", policy.getId()));
            BigDecimal applied;
            try {
                applied = pool.reserve(requested);
            } catch (IllegalStateException exception) {
                throw new ConflictException("POOL_CONDITION_NOT_MET", exception.getMessage());
            }
            if (applied.signum() <= 0) {
                continue;
            }
            poolRepository.save(pool);
            PolicyPreEntitlement pre = PolicyPreEntitlement.create(
                    "PRE-%06d".formatted(preRepository.nextPreSequence()), command.orderId(),
                    command.orderLineId(), command.serialId(), command.productId(), policy.getId(), applied,
                    Boolean.TRUE.equals(command.addedAfterOrder()), currentUserId(), command.reason(),
                    null);
            preRepository.save(pre);
            poolUsageRepository.save(PolicySubsidyPoolUsage.create(pool.getId(), "PRE_ENTITLEMENT", pre.getId(),
                    pre.getPreNo(), applied));
            publish(pre, policy, "PolicyPreEntitlementCreated");
            created.add(toPreView(pre, policy));
        }
        if (created.isEmpty()) {
            throw new ConflictException("NO_POLICY_APPLIED", "所选政策的额度已用尽，未生成预享受项");
        }
        return created;
    }

    /** 取消预享受：释放额度池。 */
    @Transactional
    public PolicyApi.PreEntitlementView cancelPre(UUID preId) {
        PolicyPreEntitlement pre = preRepository.findById(preId)
                .orElseThrow(() -> new NotFoundException("预享受项", preId));
        try {
            pre.cancel();
        } catch (IllegalStateException exception) {
            throw new ConflictException("PRE_STATUS_INVALID", exception.getMessage());
        }
        PolicySubsidyPool pool = poolRepository.findByPolicyIdForUpdate(pre.getPolicyId())
                .orElseThrow(() -> new NotFoundException("政策额度池", pre.getPolicyId()));
        pool.release(pre.getEstimateAmount());
        poolUsageRepository.save(PolicySubsidyPoolUsage.create(pool.getId(), "PRE_ENTITLEMENT_RELEASE",
                pre.getId(), pre.getPreNo(), pre.getEstimateAmount().negate()));
        poolRepository.save(pool);
        PolicyPreEntitlement saved = preRepository.save(pre);
        publish(saved, find(saved.getPolicyId()), "PolicyPreEntitlementCancelled");
        return toPreView(saved, find(saved.getPolicyId()));
    }

    @Transactional(readOnly = true)
    public List<PolicyApi.PreEntitlementView> listPre(UUID orderId) {
        List<PolicyPreEntitlement> rows = orderId == null ? preRepository.findAllByOrderByCreatedAtDesc()
                : preRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        return rows.stream().map(pre -> toPreView(pre, find(pre.getPolicyId()))).toList();
    }

    /** 实销确认后：该订单的预享受项转正式权益，按实销日期归期。 */
    @Transactional
    public List<EntitlementApi> generateFromRetail(UUID orderId, UUID retailId, LocalDate retailDate,
                                                   UUID serialId) {
        List<PolicyPreEntitlement> pres = preRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .filter(pre -> pre.getStatus() == PreEntitlementStatus.PENDING_RETAIL)
                .toList();
        if (pres.isEmpty()) {
            throw new ConflictException("NO_PRE_ENTITLEMENT", "该订单没有待转权益的预享受项");
        }
        List<EntitlementApi> created = new ArrayList<>();
        for (PolicyPreEntitlement pre : pres) {
            PromotionPolicy policy = find(pre.getPolicyId());
            PolicyEntitlement entitlement = PolicyEntitlement.create(
                    "ENT-%06d".formatted(entitlementRepository.nextEntitlementSequence()), pre.getId(),
                    policy.getId(), policy.getSupplierId(), retailId, serialId, pre.getProductId(), orderId,
                    pre.getEstimateAmount(),
                    periodCode(policy.getClaimCycle(), retailDate));
            PolicyEntitlement saved = entitlementRepository.save(entitlement);
            costAllocationRepository.save(PolicyCostAllocation.create(saved.getId(), orderId, retailId, serialId,
                    policy.getId(), policy.getCostBearer(), policy.getCostSubject(), saved.getFaceAmount(),
                    saved.getPeriodCode()));
            created.add(EntitlementApi.from(saved));
            pre.markReady();
            preRepository.save(pre);
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<EntitlementApi> listEntitlements(UUID retailId, UUID supplierId, String periodCode, String status) {
        return entitlementRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(e -> retailId == null || retailId.equals(e.getRetailId()))
                .filter(e -> supplierId == null || supplierId.equals(e.getSupplierId()))
                .filter(e -> periodCode == null || periodCode.equals(e.getPeriodCode()))
                .filter(e -> status == null || status.equals(e.getStatus()))
                .map(EntitlementApi::from).toList();
    }

    /** 按政策申报周期与实销日期生成归期编码：2026-10 / 2026Q4 / 2026。 */
    private String periodCode(String claimCycle, LocalDate retailDate) {
        if ("QUARTERLY".equals(claimCycle)) {
            return retailDate.getYear() + "Q" + ((retailDate.getMonthValue() - 1) / 3 + 1);
        }
        if ("ANNUAL".equals(claimCycle)) {
            return String.valueOf(retailDate.getYear());
        }
        return retailDate.getYear() + "-" + String.format("%02d", retailDate.getMonthValue());
    }
    private boolean matchesProduct(PromotionPolicy policy, UUID productId) {
        if (policy.getModelList() == null || policy.getModelList().isBlank()) {
            return true;
        }
        try {
            List<String> ids = objectMapper.readValue(policy.getModelList(), new TypeReference<>() {
            });
            return ids.isEmpty() || ids.contains(productId.toString());
        } catch (Exception exception) {
            return true;
        }
    }

    private String productIdsJson(List<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(productIds.stream().map(UUID::toString).toList());
        } catch (Exception exception) {
            return null;
        }
    }

    private PolicyApi.PolicyView toView(PromotionPolicy policy) {
        return PolicyApi.PolicyView.from(policy, poolOf(policy.getId()));
    }

    private PolicyApi.PreEntitlementView toPreView(PolicyPreEntitlement pre, PromotionPolicy policy) {
        return new PolicyApi.PreEntitlementView(pre.getId(), pre.getPreNo(), pre.getOrderId(),
                pre.getOrderLineId(), pre.getProductId(), pre.getSerialId(), pre.getPolicyId(),
                policy.getName(), pre.getEstimateAmount(), pre.getStatus(), pre.isAddedAfterOrder(),
                pre.getAddReason(), pre.getCreatedAt());
    }

    private PolicySubsidyPool poolOf(UUID policyId) {
        return poolRepository.findByPolicyId(policyId).orElse(null);
    }

    private PromotionPolicy find(UUID id) {
        return policyRepository.findById(id).orElseThrow(() -> new NotFoundException("政策", id));
    }

    private void publish(PolicyPreEntitlement pre, PromotionPolicy policy, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("preNo", pre.getPreNo());
        payload.put("orderId", pre.getOrderId().toString());
        payload.put("policyId", policy.getId().toString());
        payload.put("policyName", policy.getName());
        payload.put("amount", pre.getEstimateAmount());
        payload.put("status", pre.getStatus().name());
        events.publish(new EventEnvelope(UUID.randomUUID(), eventType, "PolicyPreEntitlement", pre.getId(),
                pre.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
    }

    private UUID currentUserId() {
        return actors.current().map(actor -> actor.userId()).orElse(null);
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ConflictException("POLICY_INVALID", message);
        }
        return value.trim();
    }

    private BigDecimal requireAmount(BigDecimal value, String message) {
        if (value == null) {
            throw new ConflictException("POLICY_INVALID", message);
        }
        return value;
    }

    private String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}




