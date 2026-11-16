package com.ivs.policy.report;

import com.ivs.policy.claim.OemClaim;
import com.ivs.policy.claim.OemClaimDifference;
import com.ivs.policy.claim.OemClaimDifferenceRepository;
import com.ivs.policy.claim.OemClaimLine;
import com.ivs.policy.claim.OemClaimLineRepository;
import com.ivs.policy.claim.OemClaimRepository;
import com.ivs.policy.cost.PolicyCostAllocation;
import com.ivs.policy.cost.PolicyCostAllocationRepository;
import com.ivs.policy.fund.RebateFundService;
import com.ivs.policy.fund.api.RebateFundApi;
import com.ivs.policy.policy.PolicyEntitlement;
import com.ivs.policy.policy.PolicyEntitlementRepository;
import com.ivs.policy.policy.PolicySubsidyPool;
import com.ivs.policy.policy.PolicySubsidyPoolRepository;
import com.ivs.policy.policy.PolicySubsidyPoolUsageRepository;
import com.ivs.policy.policy.PromotionPolicy;
import com.ivs.policy.policy.PromotionPolicyRepository;
import com.ivs.policy.reference.PolicyReferencePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 厂家返利应收、返利汇总、单车返利、资金池与申报差异报表。 */
@Service
public class PolicyReportService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PolicyEntitlementRepository entitlementRepository;
    private final PromotionPolicyRepository policyRepository;
    private final PolicySubsidyPoolRepository poolRepository;
    private final PolicySubsidyPoolUsageRepository poolUsageRepository;
    private final PolicyCostAllocationRepository costRepository;
    private final OemClaimRepository claimRepository;
    private final OemClaimLineRepository claimLineRepository;
    private final OemClaimDifferenceRepository differenceRepository;
    private final RebateFundService fundService;
    private final PolicyReferencePort references;

    public PolicyReportService(PolicyEntitlementRepository entitlementRepository,
                               PromotionPolicyRepository policyRepository,
                               PolicySubsidyPoolRepository poolRepository,
                               PolicySubsidyPoolUsageRepository poolUsageRepository,
                               PolicyCostAllocationRepository costRepository,
                               OemClaimRepository claimRepository,
                               OemClaimLineRepository claimLineRepository,
                               OemClaimDifferenceRepository differenceRepository,
                               RebateFundService fundService,
                               PolicyReferencePort references) {
        this.entitlementRepository = entitlementRepository;
        this.policyRepository = policyRepository;
        this.poolRepository = poolRepository;
        this.poolUsageRepository = poolUsageRepository;
        this.costRepository = costRepository;
        this.claimRepository = claimRepository;
        this.claimLineRepository = claimLineRepository;
        this.differenceRepository = differenceRepository;
        this.fundService = fundService;
        this.references = references;
    }

    @Transactional(readOnly = true)
    public List<PolicyReportApi.ReceivableAgingRow> receivableAging(UUID supplierId, String periodCode) {
        Map<UUID, PromotionPolicy> policies = policyMap();
        Map<GroupKey, List<PolicyEntitlement>> groups = new LinkedHashMap<>();
        for (PolicyEntitlement entitlement : entitlementRepository.findAllByOrderByCreatedAtDesc()) {
            if (supplierId != null && !supplierId.equals(entitlement.getSupplierId())) continue;
            if (periodCode != null && !periodCode.isBlank() && !periodCode.equals(entitlement.getPeriodCode())) continue;
            GroupKey key = new GroupKey(entitlement.getSupplierId(), entitlement.getPolicyId(), entitlement.getPeriodCode());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entitlement);
        }
        List<PolicyReportApi.ReceivableAgingRow> rows = new ArrayList<>();
        for (Map.Entry<GroupKey, List<PolicyEntitlement>> entry : groups.entrySet()) {
            GroupKey key = entry.getKey();
            BigDecimal face = sum(entry.getValue(), PolicyEntitlement::getFaceAmount);
            BigDecimal claimed = sum(entry.getValue(), PolicyEntitlement::getClaimedAmount);
            BigDecimal confirmed = sum(entry.getValue(), PolicyEntitlement::getConfirmedAmount);
            BigDecimal settled = sum(entry.getValue(), PolicyEntitlement::getSettledAmount);
            BigDecimal outstanding = face.subtract(settled).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            long ageDays = entry.getValue().stream().mapToLong(this::ageDays).max().orElse(0);
            PromotionPolicy policy = policies.get(key.policyId());
            rows.add(new PolicyReportApi.ReceivableAgingRow(key.supplierId(), references.supplierName(key.supplierId()),
                    key.policyId(), policy == null ? null : policy.getName(), key.periodCode(),
                    entry.getValue().size(), face, claimed, confirmed, settled, outstanding, ageDays, bucket(ageDays)));
        }
        rows.sort(Comparator.comparing(PolicyReportApi.ReceivableAgingRow::supplierId,
                Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(PolicyReportApi.ReceivableAgingRow::periodCode));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<PolicyReportApi.PoolProgressRow> poolProgress() {
        List<PolicyReportApi.PoolProgressRow> rows = new ArrayList<>();
        for (PromotionPolicy policy : policyRepository.findAllByOrderByPriorityDescCreatedAtDesc()) {
            PolicySubsidyPool pool = poolRepository.findByPolicyId(policy.getId()).orElse(null);
            if (pool == null) continue;
            long usageCount = poolUsageRepository.findByPoolIdOrderByUsedAtAsc(pool.getId()).stream()
                    .filter(usage -> usage.getAmount().signum() > 0).count();
            rows.add(new PolicyReportApi.PoolProgressRow(policy.getId(), policy.getPolicyNo(), policy.getName(),
                    pool.getLimitType().name(), pool.getTotalAmount(), pool.getUsedAmount(), pool.getRemainingAmount(),
                    ratio(pool.getUsedAmount(), pool.getTotalAmount()), usageCount, pool.getStatus(),
                    pool.getEffectiveFrom(), pool.getEffectiveTo()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public PolicyReportApi.FundLedgerReport fundLedger(UUID supplierId) {
        RebateFundApi.PoolView pool = fundService.overview();
        List<PolicyReportApi.FundLedgerRow> bySupplier = pool.suppliers().stream()
                .map(row -> new PolicyReportApi.FundLedgerRow(row.supplierId(), references.supplierName(row.supplierId()),
                        row.totalInAmount(), row.totalOffsetAmount(), row.balanceAmount()))
                .toList();
        return new PolicyReportApi.FundLedgerReport(pool, bySupplier, fundService.txns(supplierId),
                fundService.offsets(supplierId));
    }

    @Transactional(readOnly = true)
    public List<PolicyReportApi.UnitCostRow> unitCost(String periodCode) {
        Map<UUID, PromotionPolicy> policies = policyMap();
        List<PolicyReportApi.UnitCostRow> rows = new ArrayList<>();
        for (PolicyCostAllocation cost : costRepository.findAllByOrderByCreatedAtDesc()) {
            if (periodCode != null && !periodCode.isBlank() && !periodCode.equals(cost.getPeriodCode())) continue;
            PolicyEntitlement entitlement = entitlementRepository.findById(cost.getEntitlementId()).orElse(null);
            PromotionPolicy policy = policies.get(cost.getPolicyId());
            PolicyReferencePort.RetailInfo retail = references.retail(cost.getRetailId());
            PolicyReferencePort.PurchaseOrderInfo purchaseOrder = retail == null ? null
                    : references.purchaseOrderBySerialNo(retail.serialNo());
            OemClaimLine claimLine = claimLineRepository.findByEntitlementId(cost.getEntitlementId()).orElse(null);
            OemClaim claim = claimLine == null ? null : claimRepository.findById(claimLine.getClaimId()).orElse(null);
            rows.add(new PolicyReportApi.UnitCostRow(cost.getEntitlementId(),
                    entitlement == null ? null : entitlement.getEntitlementNo(),
                    entitlement == null ? null : entitlement.getStatus(), cost.getPeriodCode(), cost.getRetailId(),
                    retail == null ? null : retail.retailNo(), cost.getSerialId(),
                    retail == null ? null : retail.serialNo(), retail == null ? null : retail.productNo(),
                    retail == null ? null : retail.productName(), cost.getPolicyId(),
                    policy == null ? null : policy.getName(),
                    purchaseOrder == null ? null : purchaseOrder.orderId(),
                    purchaseOrder == null ? null
                            : purchaseOrder.externalOrderNo() != null ? purchaseOrder.externalOrderNo()
                            : purchaseOrder.orderNo(),
                    retail == null ? null : retail.invoiceIssuedAt(),
                    entitlement == null ? null : entitlement.getClaimedAmount(),
                    entitlement == null ? null : entitlement.getConfirmedAmount(),
                    entitlement == null ? null : entitlement.getSettledAmount(),
                    claim == null ? null : claim.getClaimNo(),
                    claim == null ? null : claim.getInvoiceNo()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<PolicyReportApi.RebateSummaryRow> rebateSummary(String periodCode) {
        Map<UUID, PromotionPolicy> policies = policyMap();
        Map<SummaryKey, List<PolicyEntitlement>> groups = new LinkedHashMap<>();
        for (PolicyEntitlement entitlement : entitlementRepository.findAllByOrderByCreatedAtDesc()) {
            if (periodCode != null && !periodCode.isBlank() && !periodCode.equals(entitlement.getPeriodCode())) continue;
            SummaryKey key = new SummaryKey(entitlement.getSupplierId(), entitlement.getPolicyId(),
                    entitlement.getPeriodCode());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(entitlement);
        }
        List<PolicyReportApi.RebateSummaryRow> rows = new ArrayList<>();
        for (Map.Entry<SummaryKey, List<PolicyEntitlement>> entry : groups.entrySet()) {
            SummaryKey key = entry.getKey();
            PromotionPolicy policy = policies.get(key.policyId());
            BigDecimal face = sum(entry.getValue(), PolicyEntitlement::getFaceAmount);
            BigDecimal claimed = sum(entry.getValue(), PolicyEntitlement::getClaimedAmount);
            BigDecimal confirmed = sum(entry.getValue(), PolicyEntitlement::getConfirmedAmount);
            BigDecimal settled = sum(entry.getValue(), PolicyEntitlement::getSettledAmount);
            BigDecimal outstanding = face.subtract(settled).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
            rows.add(new PolicyReportApi.RebateSummaryRow(key.periodCode(), key.supplierId(),
                    references.supplierName(key.supplierId()), key.policyId(),
                    policy == null ? null : policy.getName(), entry.getValue().size(), face, claimed,
                    confirmed, settled, outstanding));
        }
        rows.sort(Comparator.comparing(PolicyReportApi.RebateSummaryRow::periodCode)
                .thenComparing(PolicyReportApi.RebateSummaryRow::supplierName,
                        Comparator.nullsLast(String::compareTo)));
        return rows;
    }

    @Transactional(readOnly = true)
    public List<PolicyReportApi.ClaimDifferenceRow> claimDifferences(UUID supplierId) {
        Map<UUID, OemClaim> claims = new HashMap<>();
        for (OemClaim claim : claimRepository.findAllByOrderByCreatedAtDesc()) claims.put(claim.getId(), claim);
        List<PolicyReportApi.ClaimDifferenceRow> rows = new ArrayList<>();
        for (OemClaimDifference difference : differenceRepository.findAllByOrderByCreatedAtDesc()) {
            OemClaim claim = claims.get(difference.getClaimId());
            if (supplierId != null && (claim == null || !supplierId.equals(claim.getSupplierId()))) continue;
            PolicyEntitlement entitlement = entitlementRepository.findById(difference.getEntitlementId()).orElse(null);
            rows.add(new PolicyReportApi.ClaimDifferenceRow(difference.getId(),
                    claim == null ? null : claim.getClaimNo(),
                    claim == null ? null : references.supplierName(claim.getSupplierId()),
                    difference.getEntitlementId(), entitlement == null ? null : entitlement.getEntitlementNo(),
                    difference.getAmount(), difference.getReason(), difference.getDisposition(), difference.getStatus(),
                    difference.getCreatedAt(), difference.getResolvedAt(), ageDays(difference.getCreatedAt())));
        }
        return rows;
    }

    private Map<UUID, PromotionPolicy> policyMap() {
        Map<UUID, PromotionPolicy> map = new HashMap<>();
        for (PromotionPolicy policy : policyRepository.findAll()) map.put(policy.getId(), policy);
        return map;
    }

    private BigDecimal sum(List<PolicyEntitlement> entitlements,
                           java.util.function.Function<PolicyEntitlement, BigDecimal> getter) {
        return entitlements.stream().map(getter).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    private long ageDays(PolicyEntitlement entitlement) {
        return ageDays(entitlement.getCreatedAt());
    }

    private long ageDays(Instant instant) {
        if (instant == null) return 0;
        LocalDate start = instant.atZone(ZONE).toLocalDate();
        return Math.max(0, ChronoUnit.DAYS.between(start, LocalDate.now(ZONE)));
    }

    private String bucket(long days) {
        if (days <= 30) return "0-30";
        if (days <= 60) return "31-60";
        if (days <= 90) return "61-90";
        return "90+";
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0 || numerator == null) return BigDecimal.ZERO.setScale(2);
        return numerator.multiply(HUNDRED).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private record GroupKey(UUID supplierId, UUID policyId, String periodCode) {
    }

    private record SummaryKey(UUID supplierId, UUID policyId, String periodCode) {
    }
}
