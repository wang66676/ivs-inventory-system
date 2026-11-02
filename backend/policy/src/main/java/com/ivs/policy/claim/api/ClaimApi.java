package com.ivs.policy.claim.api;

import com.ivs.policy.claim.OemClaim;
import com.ivs.policy.claim.OemClaimDifference;
import com.ivs.policy.claim.OemClaimLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 申报单、厂家确认与差异处置契约。 */
public final class ClaimApi {

    private ClaimApi() {
    }

    public record ClaimLineView(
            UUID id, UUID entitlementId, String entitlementNo, UUID retailId, String retailNo,
            LocalDate retailDate, UUID policyId, String policyName, UUID serialId, String serialNo,
            UUID purchaseOrderId, String orderNo, BigDecimal amount,
            BigDecimal confirmedAmount, BigDecimal differenceAmount, String differenceReason,
            String differenceDisposition, Instant dispositionTime
    ) {
        public static ClaimLineView from(OemClaimLine line, String entitlementNo, String retailNo,
                                         LocalDate retailDate, String policyName, String serialNo,
                                         UUID purchaseOrderId, String orderNo) {
            return new ClaimLineView(line.getId(), line.getEntitlementId(), entitlementNo, line.getRetailId(),
                    retailNo, retailDate, line.getPolicyId(), policyName, line.getSerialId(), serialNo,
                    purchaseOrderId, orderNo, line.getAmount(), line.getConfirmedAmount(),
                    line.getDifferenceAmount(), line.getDifferenceReason(), line.getDifferenceDisposition(),
                    line.getDifferenceDisposition() == null ? null : line.getUpdatedAt());
        }
    }

    public record DifferenceView(
            UUID id, UUID claimLineId, UUID entitlementId, BigDecimal amount, String reason,
            String disposition, String status, String resolutionRemark, Instant createdAt, Instant resolvedAt
    ) {
        public static DifferenceView from(OemClaimDifference diff) {
            return new DifferenceView(diff.getId(), diff.getClaimLineId(), diff.getEntitlementId(),
                    diff.getAmount(), diff.getReason(), diff.getDisposition(), diff.getStatus(),
                    diff.getResolutionRemark(), diff.getCreatedAt(), diff.getResolvedAt());
        }
    }

    public record ClaimView(
            UUID id, String claimNo, UUID supplierId, String supplierName, UUID policyId, String policyName,
            String periodCode, String settleCycle, boolean mergedClaim, BigDecimal claimAmount,
            BigDecimal confirmedAmount, BigDecimal differenceAmount, Instant cutoffAt, String status,
            Instant submittedAt, Instant confirmedAt, Instant settledAt, Instant closedAt,
            UUID invoiceId, String invoiceNo, String remark, Instant createdAt,
            List<ClaimLineView> lines, List<DifferenceView> differences
    ) {
        public static ClaimView from(OemClaim claim, String supplierName, String policyName,
                                     List<ClaimLineView> lines, List<DifferenceView> differences) {
            return new ClaimView(claim.getId(), claim.getClaimNo(), claim.getSupplierId(), supplierName,
                    claim.getPolicyId(), policyName, claim.getPeriodCode(), claim.getSettleCycle(),
                    claim.isMergedClaim(), claim.getClaimAmount(), claim.getConfirmedAmount(),
                    claim.getDifferenceAmount(), claim.getCutoffAt(), claim.getStatus(),
                    claim.getSubmittedAt(), claim.getConfirmedAt(), claim.getSettledAt(),
                    claim.getClosedAt(), claim.getInvoiceId(), claim.getInvoiceNo(), claim.getRemark(),
                    claim.getCreatedAt(), lines, differences);
        }
    }

    /** 实销后申请兑现：把一批正式权益归集到申报单。 */
    public record ApplyClaimsCommand(List<UUID> entitlementIds, Boolean merge, String remark) {
    }

    public record ConfirmLineCommand(UUID lineId, BigDecimal confirmedAmount, String reason, String disposition) {
    }

    public record ConfirmClaimCommand(List<ConfirmLineCommand> lines, Boolean disputed, String remark) {
    }

    public record DisputeClaimCommand(String reason) {
    }

    public record ResolveDifferenceCommand(String remark) {
    }

    public record ClaimSettlementInfo(UUID claimId, String claimNo, UUID supplierId,
                                      BigDecimal confirmedAmount, String status) {
    }
}
