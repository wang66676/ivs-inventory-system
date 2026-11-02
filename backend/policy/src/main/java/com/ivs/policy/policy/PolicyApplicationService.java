package com.ivs.policy.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.policy.cost.PolicyCostAllocation;
import com.ivs.policy.cost.PolicyCostAllocationRepository;
import com.ivs.policy.policy.api.EntitlementApi;
import com.ivs.policy.policy.api.PolicyApi;
import com.ivs.policy.reference.PreEntitlementReferencePort;
import com.ivs.policy.reference.PolicyReferencePort;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final PreEntitlementReferencePort referencePort;
    private final PolicyReferencePort policyReferences;
    private final EventPublisher events;
    private final CurrentActorProvider actors;
    private final ObjectMapper objectMapper;

    public PolicyApplicationService(PromotionPolicyRepository policyRepository,
                                    PolicySubsidyPoolRepository poolRepository,
                                    PolicyPreEntitlementRepository preRepository,
                                    PolicyEntitlementRepository entitlementRepository,
                                    PolicyCostAllocationRepository costAllocationRepository,
                                    PolicySubsidyPoolUsageRepository poolUsageRepository,
                                    PreEntitlementReferencePort referencePort,
                                    PolicyReferencePort policyReferences,
                                    EventPublisher events,
                                    CurrentActorProvider actors,
                                    ObjectMapper objectMapper) {
        this.policyRepository = policyRepository;
        this.poolRepository = poolRepository;
        this.preRepository = preRepository;
        this.entitlementRepository = entitlementRepository;
        this.costAllocationRepository = costAllocationRepository;
        this.poolUsageRepository = poolUsageRepository;
        this.referencePort = referencePort;
        this.policyReferences = policyReferences;
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
        return apply(command, false);
    }

    private List<PolicyApi.PreEntitlementView> apply(PolicyApi.ApplyPreEntitlementCommand command,
                                                     boolean allowUnmetCondition) {
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
            if (preRepository.findByOrderIdAndOrderLineIdAndSerialIdAndPolicyId(command.orderId(), command.orderLineId(),
                    command.serialId(), policy.getId()).isPresent()) {
                throw new ConflictException("PRE_DUPLICATE", "同一订单行已享受政策：" + policy.getName());
            }
            BigDecimal requested = policy.benefitOf(command.orderLineAmount());
            PolicySubsidyPool pool = poolRepository.findByPolicyIdForUpdate(policy.getId())
                    .orElseThrow(() -> new NotFoundException("政策额度池", policy.getId()));
            BigDecimal applied;
            try {
                applied = allowUnmetCondition ? pool.reserveProvisional(requested) : pool.reserve(requested);
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

    /** 采购单保存后同步返利政策：先释放旧草稿预享受，再按最新明细重建。 */
    @Transactional
    public void syncFromPurchaseOrder(UUID orderId, List<PolicyApi.SyncPreEntitlementLine> lines) {
        List<PolicyPreEntitlement> existing = preRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
        for (PolicyPreEntitlement pre : existing) {
            releaseForResync(pre);
        }
        preRepository.flush();
        if (lines == null) {
            return;
        }
        for (PolicyApi.SyncPreEntitlementLine line : lines) {
            if (line == null || line.policyIds() == null || line.policyIds().isEmpty()) {
                continue;
            }
            apply(new PolicyApi.ApplyPreEntitlementCommand(orderId, line.orderLineId(), line.productId(), null,
                    line.orderLineAmount(), line.policyIds(), false, "采购单返利政策自动同步"), true);
        }
    }

    /** 收货后按每个 VIN/SN 同步一次采购政策，确保同订单每台车都有独立预享受项。 */
    @Transactional
    public void syncReceivedSerials(UUID orderId, List<PolicyApi.SyncPreEntitlementLine> lines) {
        if (lines == null) return;
        for (PolicyApi.SyncPreEntitlementLine line : lines) {
            if (line.policyIds() == null || line.policyIds().isEmpty()) continue;
            apply(new PolicyApi.ApplyPreEntitlementCommand(orderId, line.orderLineId(), line.productId(),
                    line.serialId(), line.orderLineAmount(), line.policyIds(), false,
                    "采购收货按VIN同步返利政策"), true);
        }
    }

    /** 采购草稿变更时释放旧预享受项；后续收货会按 VIN 重新生成。 */
    @Transactional
    public void clearFromPurchaseOrder(UUID orderId) {
        for (PolicyPreEntitlement pre : preRepository.findByOrderIdOrderByCreatedAtAsc(orderId)) {
            releaseForResync(pre);
        }
        preRepository.flush();
    }

    private void releaseForResync(PolicyPreEntitlement pre) {
        if (pre.getStatus() == PreEntitlementStatus.CANCELLED) {
            preRepository.delete(pre);
            return;
        }
        if (pre.getStatus() != PreEntitlementStatus.PENDING_RETAIL) {
            throw new ConflictException("PRE_STATUS_INVALID", "采购单政策已生成正式权益，不能再修改预享受项");
        }
        pre.cancel();
        PolicySubsidyPool pool = poolRepository.findByPolicyIdForUpdate(pre.getPolicyId())
                .orElseThrow(() -> new NotFoundException("政策额度池", pre.getPolicyId()));
        pool.release(pre.getEstimateAmount());
        poolUsageRepository.save(PolicySubsidyPoolUsage.create(pool.getId(), "PRE_ENTITLEMENT_RELEASE",
                pre.getId(), pre.getPreNo(), pre.getEstimateAmount().negate()));
        poolRepository.save(pool);
        preRepository.delete(pre);
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

    /** 实销确认后：仅把当前 VIN/SN 对应的预享受项转正式权益，按实销日期归期。 */
    @Transactional
    public List<EntitlementApi> generateFromRetail(UUID orderId, UUID retailId, LocalDate retailDate,
                                                   UUID serialId) {
        if (serialId == null) {
            throw new ConflictException("RETAIL_ENTITLEMENT_SERIAL_REQUIRED", "生成权益必须指定 VIN/SN");
        }
        List<PolicyPreEntitlement> allPres = preRepository.findByOrderIdForUpdate(orderId);
        reconcileLegacyPreEntitlements(orderId, serialId, allPres);
        List<PolicyPreEntitlement> pres = allPres.stream()
                .filter(pre -> pre.getStatus() == PreEntitlementStatus.PENDING_RETAIL)
                .filter(pre -> serialId.equals(pre.getSerialId()))
                .toList();
        if (pres.isEmpty()) {
            List<EntitlementApi> existing = entitlementRepository.findByRetailId(retailId).stream()
                    .filter(entitlement -> serialId.equals(entitlement.getSerialId()))
                    .filter(entitlement -> orderId.equals(entitlement.getOrderId()))
                    .map(this::toEntitlementView)
                    .toList();
            if (!existing.isEmpty()) {
                return existing;
            }
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

    /**
     * 修复升级前的行级预享受项：历史记录没有 serial_id，首次生成时会被误当成该订单所有 VIN 共用。
     * 这里把它认领到一个 VIN，并为同一采购明细下的其余已收货 VIN 补齐独立预享受项。
     */
    private void reconcileLegacyPreEntitlements(UUID orderId, UUID currentSerialId,
                                                List<PolicyPreEntitlement> allPres) {
        List<PolicyPreEntitlement> legacy = allPres.stream()
                .filter(pre -> pre.getSerialId() == null)
                .toList();
        if (legacy.isEmpty()) return;

        Map<UUID, List<UUID>> receivedByLine = new LinkedHashMap<>();
        for (PreEntitlementReferencePort.ReceivedSerial received : referencePort.receivedSerials(orderId)) {
            receivedByLine.computeIfAbsent(received.orderLineId(), key -> new ArrayList<>()).add(received.serialId());
        }

        Map<PreKey, PolicyPreEntitlement> templates = new LinkedHashMap<>();
        for (PolicyPreEntitlement pre : legacy) {
            templates.putIfAbsent(new PreKey(pre.getOrderLineId(), pre.getPolicyId()), pre);
        }

        for (PolicyPreEntitlement pre : legacy) {
            PolicyEntitlement linkedEntitlement = entitlementRepository
                    .findByPreEntitlementId(pre.getId()).orElse(null);
            if (linkedEntitlement != null && linkedEntitlement.getSerialId() != null) {
                if (!hasSerialPre(allPres, pre, linkedEntitlement.getSerialId())) {
                    pre.assignSerial(linkedEntitlement.getSerialId());
                    preRepository.save(pre);
                }
                continue;
            }

            UUID targetSerialId = chooseLegacySerial(pre, currentSerialId, receivedByLine, allPres);
            if (targetSerialId != null) {
                pre.assignSerial(targetSerialId);
                preRepository.save(pre);
            } else if (pre.getStatus() == PreEntitlementStatus.PENDING_RETAIL) {
                releaseForResync(pre);
            }
        }

        for (Map.Entry<PreKey, PolicyPreEntitlement> entry : templates.entrySet()) {
            PreKey key = entry.getKey();
            PolicyPreEntitlement template = entry.getValue();
            if (key.orderLineId() == null) continue;
            for (UUID receivedSerialId : receivedByLine.getOrDefault(key.orderLineId(), List.of())) {
                if (!hasSerialPre(allPres, template, receivedSerialId)) {
                    allPres.add(clonePreForSerial(template, receivedSerialId));
                }
            }
        }
    }

    private UUID chooseLegacySerial(PolicyPreEntitlement pre, UUID currentSerialId,
                                    Map<UUID, List<UUID>> receivedByLine,
                                    List<PolicyPreEntitlement> allPres) {
        List<UUID> received = receivedByLine.getOrDefault(pre.getOrderLineId(), List.of());
        if (currentSerialId != null && !hasSerialPre(allPres, pre, currentSerialId)
                && (received.isEmpty() || received.contains(currentSerialId))) {
            return currentSerialId;
        }
        for (UUID serialId : received) {
            if (!hasSerialPre(allPres, pre, serialId)) return serialId;
        }
        return currentSerialId != null && !hasSerialPre(allPres, pre, currentSerialId) ? currentSerialId : null;
    }

    private boolean hasSerialPre(List<PolicyPreEntitlement> allPres, PolicyPreEntitlement template,
                                 UUID serialId) {
        return allPres.stream().anyMatch(pre -> serialId.equals(pre.getSerialId())
                && java.util.Objects.equals(pre.getOrderLineId(), template.getOrderLineId())
                && pre.getPolicyId().equals(template.getPolicyId()));
    }

    private PolicyPreEntitlement clonePreForSerial(PolicyPreEntitlement template, UUID serialId) {
        PolicySubsidyPool pool = poolRepository.findByPolicyIdForUpdate(template.getPolicyId())
                .orElseThrow(() -> new NotFoundException("政策额度池", template.getPolicyId()));
        BigDecimal applied = pool.reserveProvisional(template.getEstimateAmount());
        if (applied.signum() <= 0) {
            throw new ConflictException("PRE_LEGACY_REPLENISH_FAILED",
                    "历史预享受项按 VIN 补齐时政策额度已用尽");
        }
        poolRepository.save(pool);
        PolicyPreEntitlement created = PolicyPreEntitlement.create(
                "PRE-%06d".formatted(preRepository.nextPreSequence()), template.getOrderId(),
                template.getOrderLineId(), serialId, template.getProductId(), template.getPolicyId(), applied,
                template.isAddedAfterOrder(), currentUserId(), template.getAddReason(), template.getRemark());
        created = preRepository.save(created);
        poolUsageRepository.save(PolicySubsidyPoolUsage.create(pool.getId(), "PRE_ENTITLEMENT", created.getId(),
                created.getPreNo(), applied));
        publish(created, find(created.getPolicyId()), "PolicyPreEntitlementCreated");
        return created;
    }

    @Transactional(readOnly = true)
    public List<EntitlementApi> listEntitlements(UUID retailId, UUID supplierId, String periodCode, String status) {
        return entitlementRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(e -> retailId == null || retailId.equals(e.getRetailId()))
                .filter(e -> supplierId == null || supplierId.equals(e.getSupplierId()))
                .filter(e -> periodCode == null || periodCode.equals(e.getPeriodCode()))
                .filter(e -> status == null || status.equals(e.getStatus()))
                .map(this::toEntitlementView).toList();
    }

    private EntitlementApi toEntitlementView(PolicyEntitlement entitlement) {
        PromotionPolicy policy = policyRepository.findById(entitlement.getPolicyId()).orElse(null);
        com.ivs.policy.reference.PolicyReferencePort.RetailInfo retail = entitlement.getRetailId() == null ? null
                : policyReferences.retail(entitlement.getRetailId());
        com.ivs.policy.reference.PolicyReferencePort.PurchaseOrderInfo purchaseOrder =
                retail == null ? null : policyReferences.purchaseOrderBySerialNo(retail.serialNo());
        return new EntitlementApi(entitlement.getId(), entitlement.getEntitlementNo(), entitlement.getPolicyId(),
                entitlement.getSupplierId(), entitlement.getRetailId(), entitlement.getSerialId(),
                entitlement.getProductId(), entitlement.getOrderId(), entitlement.getFaceAmount(),
                entitlement.getClaimedAmount(), entitlement.getConfirmedAmount(), entitlement.getSettledAmount(),
                entitlement.getPeriodCode(), entitlement.getStatus(), entitlement.getCreatedAt(),
                policy == null ? null : policy.getName(),
                purchaseOrder == null ? null : purchaseOrder.orderId(),
                purchaseOrder == null ? null
                        : purchaseOrder.externalOrderNo() != null ? purchaseOrder.externalOrderNo()
                        : purchaseOrder.orderNo(),
                retail == null ? null : retail.invoiceIssuedAt());
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
        PreEntitlementReferencePort.OrderReference reference = referencePort.describe(
                pre.getOrderId(), pre.getOrderLineId(), pre.getSerialId());
        return new PolicyApi.PreEntitlementView(pre.getId(), pre.getPreNo(), pre.getOrderId(),
                pre.getOrderLineId(), pre.getProductId(), pre.getSerialId(), pre.getPolicyId(),
                policy.getName(), pre.getEstimateAmount(), pre.getStatus(), pre.isAddedAfterOrder(),
                pre.getAddReason(), reference.serialNos(), reference.orderNo(), reference.invoiceIssuedAt(),
                reference.retailDate(), pre.getCreatedAt());
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

    private record PreKey(UUID orderLineId, UUID policyId) {
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




