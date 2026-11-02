package com.ivs.policy.claim;

import com.ivs.policy.claim.api.ClaimApi;
import com.ivs.policy.cost.PolicyCostAllocationRepository;
import com.ivs.policy.policy.PolicyEntitlement;
import com.ivs.policy.policy.PolicyEntitlementRepository;
import com.ivs.policy.policy.PolicyPreEntitlement;
import com.ivs.policy.policy.PolicyPreEntitlementRepository;
import com.ivs.policy.policy.PromotionPolicy;
import com.ivs.policy.policy.PromotionPolicyRepository;
import com.ivs.policy.reference.PolicyReferencePort;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.event.EventEnvelope;
import com.ivs.shared.event.EventPublisher;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** 实销后兑现申请、申报单归集、厂家确认、差异处置与红字结算。 */
@Service
public class ClaimApplicationService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final OemClaimRepository claimRepository;
    private final OemClaimLineRepository lineRepository;
    private final OemClaimDifferenceRepository differenceRepository;
    private final PolicyEntitlementRepository entitlementRepository;
    private final PolicyPreEntitlementRepository preRepository;
    private final PromotionPolicyRepository policyRepository;
    private final PolicyCostAllocationRepository costRepository;
    private final PolicyReferencePort references;
    private final EventPublisher events;
    private final CurrentActorProvider actors;

    public ClaimApplicationService(OemClaimRepository claimRepository,
                                   OemClaimLineRepository lineRepository,
                                   OemClaimDifferenceRepository differenceRepository,
                                   PolicyEntitlementRepository entitlementRepository,
                                   PolicyPreEntitlementRepository preRepository,
                                   PromotionPolicyRepository policyRepository,
                                   PolicyCostAllocationRepository costRepository,
                                   PolicyReferencePort references,
                                   EventPublisher events,
                                   CurrentActorProvider actors) {
        this.claimRepository = claimRepository;
        this.lineRepository = lineRepository;
        this.differenceRepository = differenceRepository;
        this.entitlementRepository = entitlementRepository;
        this.preRepository = preRepository;
        this.policyRepository = policyRepository;
        this.costRepository = costRepository;
        this.references = references;
        this.events = events;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public List<ClaimApi.ClaimView> list(UUID supplierId, String periodCode, String status) {
        List<OemClaim> rows = supplierId != null ? claimRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId)
                : periodCode != null ? claimRepository.findByPeriodCodeOrderByCreatedAtDesc(periodCode)
                : claimRepository.findAllByOrderByCreatedAtDesc();
        return rows.stream()
                .filter(row -> status == null || status.equals(row.getStatus()))
                .map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public ClaimApi.ClaimView get(UUID id) {
        return toView(find(id));
    }

    /**
     * 实销后申请兑现：把待申报权益归集到申报单。
     * merge=false 按「厂家 + 政策 + 周期」单张；merge=true 按「厂家 + 周期」合并。
     */
    @Transactional
    public List<ClaimApi.ClaimView> apply(ClaimApi.ApplyClaimsCommand command) {
        if (command == null || command.entitlementIds() == null || command.entitlementIds().isEmpty()) {
            throw new ConflictException("ENTITLEMENT_REQUIRED", "请至少选择一条待申请权益");
        }
        boolean merge = Boolean.TRUE.equals(command.merge());
        LinkedHashMap<ClaimKey, List<PolicyEntitlement>> groups = new LinkedHashMap<>();
        for (UUID entitlementId : new LinkedHashSet<>(command.entitlementIds())) {
            PolicyEntitlement entitlement = entitlementRepository.findById(entitlementId)
                    .orElseThrow(() -> new NotFoundException("政策权益", entitlementId));
            if (!"PENDING_CLAIM".equals(entitlement.getStatus())) {
                throw new ConflictException("ENTITLEMENT_STATUS_INVALID",
                        "权益 " + entitlement.getEntitlementNo() + " 当前状态为 " + entitlement.getStatus()
                                + "，只有待申请权益可以归集");
            }
            PromotionPolicy policy = policyRepository.findById(entitlement.getPolicyId())
                    .orElseThrow(() -> new NotFoundException("政策", entitlement.getPolicyId()));
            UUID supplierId = entitlement.getSupplierId() != null ? entitlement.getSupplierId() : policy.getSupplierId();
            if (supplierId == null) {
                throw new ConflictException("POLICY_SUPPLIER_REQUIRED",
                        "政策「" + policy.getName() + "」未绑定 OEM 厂家，无法生成申报单");
            }
            ClaimKey key = new ClaimKey(supplierId, merge ? null : policy.getId(), entitlement.getPeriodCode(), merge);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entitlement);
        }

        List<ClaimApi.ClaimView> result = new ArrayList<>();
        for (Map.Entry<ClaimKey, List<PolicyEntitlement>> entry : groups.entrySet()) {
            ClaimKey key = entry.getKey();
            OemClaim claim = findDraft(key);
            if (claim == null) {
                PromotionPolicy firstPolicy = findPolicy(entry.getValue().get(0).getPolicyId());
                String cycle = key.merge() ? mergedCycle(entry.getValue()) : firstPolicy.getClaimCycle();
                Instant cutoff = key.merge() ? latestCutoff(entry.getValue())
                        : cutoffOf(entry.getValue().get(0).getPeriodCode(), cycle, firstPolicy.getClaimCutoffDay());
                claim = OemClaim.create("CLM-%06d".formatted(claimRepository.nextClaimSequence()),
                        key.supplierId(), key.policyId(), key.periodCode(), cycle, key.merge(), cutoff,
                        command.remark());
                claimRepository.save(claim);
            }
            for (PolicyEntitlement entitlement : entry.getValue()) {
                if (lineRepository.findByEntitlementId(entitlement.getId()).isPresent()) {
                    throw new ConflictException("ENTITLEMENT_ALREADY_CLAIMED",
                            "权益 " + entitlement.getEntitlementNo() + " 已进入申报单");
                }
                claim.addAmount(entitlement.getFaceAmount());
                lineRepository.save(OemClaimLine.create(claim.getId(), entitlement.getId(), entitlement.getRetailId(),
                        entitlement.getPolicyId(), entitlement.getSerialId(), entitlement.getFaceAmount()));
                entitlement.markClaimed();
                entitlementRepository.save(entitlement);
                if (entitlement.getPreEntitlementId() != null) {
                    preRepository.findById(entitlement.getPreEntitlementId()).ifPresent(pre -> {
                        pre.markApplied();
                        preRepository.save(pre);
                    });
                }
            }
            claimRepository.save(claim);
            publish(claim, "OemClaimDrafted", Map.of("claimNo", claim.getClaimNo(),
                    "claimAmount", claim.getClaimAmount()));
            result.add(toView(claim));
        }
        return result;
    }

    /** 按期间一键生成申报草稿（适用于截止日批量归集）。 */
    @Transactional
    public List<ClaimApi.ClaimView> generate(String periodCode, boolean merge, String remark) {
        if (periodCode == null || periodCode.isBlank()) {
            throw new ConflictException("CLAIM_PERIOD_REQUIRED", "请指定申报期间");
        }
        List<UUID> ids = entitlementRepository.findByPeriodCodeOrderByCreatedAtAsc(periodCode).stream()
                .filter(e -> "PENDING_CLAIM".equals(e.getStatus()))
                .map(PolicyEntitlement::getId).toList();
        if (ids.isEmpty()) {
            throw new ConflictException("NO_ENTITLEMENT_TO_CLAIM", "该期间没有待申请权益");
        }
        return apply(new ClaimApi.ApplyClaimsCommand(ids, merge, remark));
    }

    @Transactional
    public ClaimApi.ClaimView submit(UUID id) {
        OemClaim claim = find(id);
        try {
            claim.submit();
        } catch (IllegalStateException exception) {
            throw new ConflictException("CLAIM_STATUS_INVALID", exception.getMessage());
        }
        for (OemClaimLine line : lineRepository.findByClaimIdOrderByCreatedAtAsc(id)) {
            PolicyEntitlement entitlement = findEntitlement(line.getEntitlementId());
            entitlement.markSubmitted();
            entitlementRepository.save(entitlement);
        }
        ClaimApi.ClaimView view = toView(claimRepository.save(claim));
        publish(claim, "OemClaimSubmitted", Map.of("claimNo", claim.getClaimNo(),
                "claimAmount", claim.getClaimAmount()));
        return view;
    }

    /** 厂家确认：逐行登记确认额；差异行强制原因与处置，并同步政策成本。 */
    @Transactional
    public ClaimApi.ClaimView confirm(UUID id, ClaimApi.ConfirmClaimCommand command) {
        OemClaim claim = find(id);
        List<OemClaimLine> lines = lineRepository.findByClaimIdOrderByCreatedAtAsc(id);
        if (lines.isEmpty()) {
            throw new ConflictException("CLAIM_LINE_EMPTY", "申报单没有明细");
        }
        if (command == null || command.lines() == null || command.lines().size() != lines.size()) {
            throw new ConflictException("CLAIM_CONFIRM_LINES_REQUIRED", "请逐行填写厂家确认金额");
        }
        Map<UUID, ClaimApi.ConfirmLineCommand> commands = new HashMap<>();
        for (ClaimApi.ConfirmLineCommand line : command.lines()) {
            if (line.lineId() == null) {
                throw new ConflictException("CLAIM_CONFIRM_LINE_INVALID", "缺少申报明细标识");
            }
            commands.put(line.lineId(), line);
        }
        differenceRepository.deleteByClaimId(claim.getId());
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        boolean disputed = Boolean.TRUE.equals(command.disputed());
        for (OemClaimLine line : lines) {
            ClaimApi.ConfirmLineCommand lineCommand = commands.get(line.getId());
            if (lineCommand == null) {
                throw new ConflictException("CLAIM_CONFIRM_LINE_INVALID", "请填写所有申报明细的确认金额");
            }
            try {
                line.confirm(lineCommand.confirmedAmount(), lineCommand.reason(), lineCommand.disposition());
            } catch (IllegalArgumentException exception) {
                throw new ConflictException("CLAIM_CONFIRM_INVALID", exception.getMessage());
            }
            lineRepository.save(line);
            if (line.getDifferenceAmount().signum() > 0) {
                differenceRepository.save(OemClaimDifference.create(claim.getId(), line.getId(),
                        line.getEntitlementId(), line.getDifferenceAmount(), line.getDifferenceReason(),
                        line.getDifferenceDisposition()));
            }
            PolicyEntitlement entitlement = findEntitlement(line.getEntitlementId());
            entitlement.confirm(line.getConfirmedAmount(), disputed);
            entitlementRepository.save(entitlement);
            costRepository.findByEntitlementId(entitlement.getId())
                    .ifPresent(cost -> { cost.recognize(line.getConfirmedAmount()); costRepository.save(cost); });
            total = total.add(line.getConfirmedAmount());
        }
        try {
            claim.confirm(total, disputed);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ConflictException("CLAIM_CONFIRM_INVALID", exception.getMessage());
        }
        ClaimApi.ClaimView view = toView(claimRepository.save(claim));
        publish(claim, "OemClaimConfirmed", Map.of("claimNo", claim.getClaimNo(),
                "confirmedAmount", claim.getConfirmedAmount(), "differenceAmount", claim.getDifferenceAmount()));
        return view;
    }

    /** 厂家拒付/争议：整单确认为 0，逐行生成差异并转 APPEAL。 */
    @Transactional
    public ClaimApi.ClaimView dispute(UUID id, ClaimApi.DisputeClaimCommand command) {
        if (command == null || command.reason() == null || command.reason().isBlank()) {
            throw new ConflictException("CLAIM_DISPUTE_REASON_REQUIRED", "请填写争议原因");
        }
        OemClaim claim = find(id);
        List<OemClaimLine> lines = lineRepository.findByClaimIdOrderByCreatedAtAsc(id);
        List<ClaimApi.ConfirmLineCommand> commands = lines.stream()
                .map(line -> new ClaimApi.ConfirmLineCommand(line.getId(), BigDecimal.ZERO,
                        command.reason().trim(), "APPEAL")).toList();
        return confirm(id, new ClaimApi.ConfirmClaimCommand(commands, true, claim.getRemark()));
    }

    @Transactional
    public ClaimApi.ClaimView resolveDifference(UUID claimId, UUID differenceId,
                                                ClaimApi.ResolveDifferenceCommand command) {
        OemClaimDifference difference = differenceRepository.findById(differenceId)
                .orElseThrow(() -> new NotFoundException("申报差异", differenceId));
        if (!claimId.equals(difference.getClaimId())) {
            throw new ConflictException("CLAIM_DIFFERENCE_MISMATCH", "差异不属于该申报单");
        }
        difference.resolve(command == null ? null : command.remark());
        differenceRepository.save(difference);
        return toView(find(claimId));
    }

    @Transactional
    public ClaimApi.ClaimView close(UUID id) {
        OemClaim claim = find(id);
        try {
            claim.close();
        } catch (IllegalStateException exception) {
            throw new ConflictException("CLAIM_STATUS_INVALID", exception.getMessage());
        }
        return toView(claimRepository.save(claim));
    }

    /** 红字登记前校验：申报单已确认、厂家一致、红字金额等于厂家确认额。 */
    @Transactional(readOnly = true)
    public ClaimApi.ClaimSettlementInfo validateForInvoice(UUID claimId, UUID supplierId, BigDecimal amount) {
        OemClaim claim = find(claimId);
        if (!claim.getSupplierId().equals(supplierId)) {
            throw new ConflictException("CLAIM_SUPPLIER_MISMATCH", "红字发票厂家与申报单厂家不一致");
        }
        if (!"CONFIRMED".equals(claim.getStatus()) && !"PARTIALLY_CONFIRMED".equals(claim.getStatus())) {
            throw new ConflictException("CLAIM_NOT_CONFIRMED", "申报单尚未完成厂家确认，不能登记红字");
        }
        BigDecimal invoiceAmount = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        if (invoiceAmount.compareTo(claim.getConfirmedAmount()) != 0) {
            throw new ConflictException("CLAIM_INVOICE_AMOUNT_MISMATCH",
                    "红字发票金额必须等于厂家确认额 " + claim.getConfirmedAmount());
        }
        return new ClaimApi.ClaimSettlementInfo(claim.getId(), claim.getClaimNo(), claim.getSupplierId(),
                claim.getConfirmedAmount(), claim.getStatus());
    }

    /** 红字发票登记后：权益已兑现、成本按确认额落账、申报单转已结算。 */
    @Transactional
    public void settleFromInvoice(UUID claimId, UUID invoiceId, String invoiceNo) {
        OemClaim claim = find(claimId);
        for (OemClaimLine line : lineRepository.findByClaimIdOrderByCreatedAtAsc(claimId)) {
            PolicyEntitlement entitlement = findEntitlement(line.getEntitlementId());
            entitlement.settle(line.getConfirmedAmount());
            entitlementRepository.save(entitlement);
            if (entitlement.getPreEntitlementId() != null) {
                preRepository.findById(entitlement.getPreEntitlementId()).ifPresent(pre -> {
                    pre.markSettled();
                    preRepository.save(pre);
                });
            }
            costRepository.findByEntitlementId(entitlement.getId()).ifPresent(cost -> {
                cost.recognize(line.getConfirmedAmount());
                costRepository.save(cost);
            });
        }
        claim.settle(invoiceId, invoiceNo);
        claimRepository.save(claim);
        publish(claim, "OemClaimSettled", Map.of("claimNo", claim.getClaimNo(),
                "invoiceNo", invoiceNo, "confirmedAmount", claim.getConfirmedAmount()));
    }

    private OemClaim findDraft(ClaimKey key) {
        if (key.merge()) {
            return claimRepository.findFirstBySupplierIdAndPeriodCodeAndPolicyIdIsNullAndMergedClaimTrueAndStatusOrderByCreatedAtAsc(
                    key.supplierId(), key.periodCode(), "DRAFT").orElse(null);
        }
        return claimRepository.findFirstBySupplierIdAndPeriodCodeAndPolicyIdAndStatusOrderByCreatedAtAsc(
                key.supplierId(), key.periodCode(), key.policyId(), "DRAFT").orElse(null);
    }

    private ClaimApi.ClaimView toView(OemClaim claim) {
        List<ClaimApi.ClaimLineView> lines = lineRepository.findByClaimIdOrderByCreatedAtAsc(claim.getId()).stream()
                .map(line -> {
                    PolicyEntitlement entitlement = entitlementRepository.findById(line.getEntitlementId()).orElse(null);
                    PolicyReferencePort.RetailInfo retail = line.getRetailId() == null ? null
                            : references.retail(line.getRetailId());
                    PolicyReferencePort.PurchaseOrderInfo purchaseOrder = retail == null ? null
                            : references.purchaseOrderBySerialNo(retail.serialNo());
                    PromotionPolicy policy = line.getPolicyId() == null ? null
                            : policyRepository.findById(line.getPolicyId()).orElse(null);
                    return ClaimApi.ClaimLineView.from(line,
                            entitlement == null ? null : entitlement.getEntitlementNo(),
                            retail == null ? null : retail.retailNo(),
                            retail == null ? null : retail.retailDate(),
                            policy == null ? null : policy.getName(),
                            retail == null ? null : retail.serialNo(),
                            purchaseOrder == null ? null : purchaseOrder.orderId(),
                            purchaseOrder == null ? null
                                    : purchaseOrder.externalOrderNo() != null ? purchaseOrder.externalOrderNo()
                                    : purchaseOrder.orderNo());
                }).toList();
        List<ClaimApi.DifferenceView> differences = differenceRepository
                .findByClaimIdOrderByCreatedAtAsc(claim.getId()).stream()
                .map(ClaimApi.DifferenceView::from).toList();
        String supplierName = references.supplierName(claim.getSupplierId());
        String policyName = claim.getPolicyId() == null ? null
                : policyRepository.findById(claim.getPolicyId()).map(PromotionPolicy::getName).orElse(null);
        return ClaimApi.ClaimView.from(claim, supplierName, policyName, lines, differences);
    }

    private String mergedCycle(List<PolicyEntitlement> entitlements) {
        Set<String> cycles = new LinkedHashSet<>();
        for (PolicyEntitlement entitlement : entitlements) {
            policyRepository.findById(entitlement.getPolicyId())
                    .ifPresent(policy -> cycles.add(policy.getClaimCycle()));
        }
        return cycles.size() == 1 ? cycles.iterator().next() : "MIXED";
    }

    private Instant latestCutoff(List<PolicyEntitlement> entitlements) {
        Instant latest = null;
        for (PolicyEntitlement entitlement : entitlements) {
            PromotionPolicy policy = findPolicy(entitlement.getPolicyId());
            Instant cutoff = cutoffOf(entitlement.getPeriodCode(), policy.getClaimCycle(), policy.getClaimCutoffDay());
            if (latest == null || cutoff.isAfter(latest)) latest = cutoff;
        }
        return latest;
    }

    private Instant cutoffOf(String periodCode, String cycle, int cutoffDay) {
        LocalDate end;
        if (periodCode.contains("Q")) {
            int year = Integer.parseInt(periodCode.substring(0, 4));
            int quarter = Integer.parseInt(periodCode.substring(5));
            end = LocalDate.of(year, quarter * 3, 1).withDayOfMonth(
                    LocalDate.of(year, quarter * 3, 1).lengthOfMonth());
        } else if (periodCode.length() == 4) {
            end = LocalDate.of(Integer.parseInt(periodCode), 12, 31);
        } else {
            int year = Integer.parseInt(periodCode.substring(0, 4));
            int month = Integer.parseInt(periodCode.substring(5));
            end = LocalDate.of(year, month, 1).withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth());
        }
        return end.plusDays(Math.max(0, cutoffDay)).atStartOfDay(ZONE).toInstant();
    }

    private PromotionPolicy findPolicy(UUID id) {
        return policyRepository.findById(id).orElseThrow(() -> new NotFoundException("政策", id));
    }

    private PolicyEntitlement findEntitlement(UUID id) {
        return entitlementRepository.findById(id).orElseThrow(() -> new NotFoundException("政策权益", id));
    }

    private OemClaim find(UUID id) {
        return claimRepository.findById(id).orElseThrow(() -> new NotFoundException("申报单", id));
    }

    private void publish(OemClaim claim, String eventType, Map<String, Object> payload) {
        events.publish(new EventEnvelope(UUID.randomUUID(), eventType, "OemClaim", claim.getId(),
                claim.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
    }

    private record ClaimKey(UUID supplierId, UUID policyId, String periodCode, boolean merge) {
    }
}

