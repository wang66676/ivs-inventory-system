package com.ivs.app.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 跨模块经营报表契约（app 层聚合，业务模块之间不直接依赖）。 */
public final class ReportApi {

    private ReportApi() {
    }

    public record MonthlyRow(
            String period, BigDecimal purchaseAmount, BigDecimal purchaseReturnAmount, BigDecimal netPurchaseAmount,
            BigDecimal salesAmount, BigDecimal salesReturnAmount, BigDecimal netSalesAmount,
            BigDecimal costAmount, BigDecimal salesReturnCostAmount, BigDecimal grossProfit,
            BigDecimal rebateCost, BigDecimal transferOutAmount, BigDecimal transferInAmount, BigDecimal stockValue
    ) {
    }

    public record GrossProfitRow(
            UUID productId, String productNo, String productName, BigDecimal salesQuantity,
            BigDecimal salesAmount, BigDecimal salesReturnAmount, BigDecimal netSalesAmount,
            BigDecimal costAmount, BigDecimal salesReturnCostAmount, BigDecimal netCostAmount,
            BigDecimal grossProfit, BigDecimal grossMargin, BigDecimal rebateCost,
            BigDecimal netProfit, BigDecimal netMargin
    ) {
    }

    public record StockFundRow(
            UUID warehouseId, String warehouseName, UUID productId, String productNo, String productName,
            String categoryName, BigDecimal onHandQty, BigDecimal lockedQty, BigDecimal amount,
            BigDecimal share, long ageDays
    ) {
    }

    public record StockFundReport(
            BigDecimal totalAmount, BigDecimal totalOnHandQty, List<StockFundRow> rows
    ) {
    }

    public record MonthlyReport(
            LocalDate from, LocalDate to, List<MonthlyRow> rows
    ) {
    }

    public record GrossProfitReport(
            LocalDate from, LocalDate to, List<GrossProfitRow> rows
    ) {
    }
}
