package com.ivs.policy.report;

import com.ivs.policy.fund.api.RebateFundApi;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 政策与返利报表契约。 */
public final class PolicyReportApi {

    private PolicyReportApi() {
    }

    public record ReceivableAgingRow(
            UUID supplierId, String supplierName, UUID policyId, String policyName, String periodCode,
            int entitlementCount, BigDecimal faceAmount, BigDecimal claimedAmount, BigDecimal confirmedAmount,
            BigDecimal settledAmount, BigDecimal outstandingAmount, long ageDays, String bucket
    ) {
    }

    public record PoolProgressRow(
            UUID policyId, String policyNo, String policyName, String limitType, BigDecimal totalAmount,
            BigDecimal usedAmount, BigDecimal remainingAmount, BigDecimal usageRate, long usageCount,
            String status, java.time.LocalDate effectiveFrom, java.time.LocalDate effectiveTo
    ) {
    }

    public record FundLedgerRow(
            UUID supplierId, String supplierName, BigDecimal totalInAmount, BigDecimal totalOffsetAmount,
            BigDecimal balanceAmount
    ) {
    }

    public record FundLedgerReport(
            RebateFundApi.PoolView pool, List<FundLedgerRow> bySupplier,
            List<RebateFundApi.TxnView> transactions, List<RebateFundApi.OffsetView> offsets
    ) {
    }

    public record UnitCostRow(
            UUID entitlementId, String entitlementNo, String status, String periodCode,
            UUID retailId, String retailNo, UUID serialId, String serialNo, String productNo, String productName,
            UUID policyId, String policyName, UUID purchaseOrderId, String purchaseOrderNo,
            Instant invoiceIssuedAt, BigDecimal salePrice, BigDecimal purchaseCost, BigDecimal policyCost,
            BigDecimal netPrice
    ) {
    }

    public record CostRateRow(
            String periodCode, UUID productId, String productNo, String productName,
            BigDecimal policyCost, BigDecimal salesRevenue, BigDecimal costRate
    ) {
    }

    public record ClaimDifferenceRow(
            UUID id, String claimNo, String supplierName, UUID entitlementId, String entitlementNo,
            BigDecimal amount, String reason, String disposition, String status,
            Instant createdAt, Instant resolvedAt, long ageDays
    ) {
    }
}
