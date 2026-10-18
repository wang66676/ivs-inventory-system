package com.ivs.app.migration;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 历史数据迁移契约。 */
public final class MigrationApi {

    private MigrationApi() {
    }

    public record CustomerRow(String code, String name, String customerType, String contactName,
                              String contactPhone, String taxNo, BigDecimal creditLimit, String remark) {
    }

    public record SupplierRow(String code, String name, String supplierType, String contactName,
                              String contactPhone, String taxNo, String remark) {
    }

    public record CategoryRow(String code, String name, String trackingMode, String statusTemplate,
                              String unit, BigDecimal defaultTaxRate, Integer sortOrder, String remark) {
    }

    public record ProductRow(String productNo, String name, String categoryCode, String unit, String brand,
                             String series, String model, BigDecimal minPrice, BigDecimal salePriceGuide,
                             BigDecimal purchasePriceRef, BigDecimal taxRate, String remark) {
    }

    public record SerialRow(String serialNo, String productNo, String warehouseCode, BigDecimal purchasePrice,
                            Instant inboundAt, String attrsJson, String remark) {
    }

    public record OpeningStockRow(String productNo, String warehouseCode, String batchNo, BigDecimal quantity,
                                  BigDecimal unitCost, String serialNo, String remark) {
    }

    public record ReceivableRow(String receivableNo, String customerCode, BigDecimal amount,
                                BigDecimal receivedAmount, LocalDate dueDate, String remark) {
    }

    public record FundOpeningRow(String supplierCode, BigDecimal amount, String sourceNo, String remark) {
    }

    public record PayableRow(String payableNo, String supplierCode, BigDecimal amount,
                             BigDecimal paidAmount, LocalDate dueDate, String remark) {
    }

    public record Payload(
            String name, String sourceSystem, String remark,
            List<CustomerRow> customers, List<SupplierRow> suppliers, List<CategoryRow> categories,
            List<ProductRow> products, List<SerialRow> serials, List<OpeningStockRow> openingStocks,
            List<ReceivableRow> receivables, List<FundOpeningRow> fundOpenings, List<PayableRow> payables
    ) {
    }

    public record ValidationView(boolean valid, List<String> errors, Map<String, Integer> counts,
                                 BigDecimal totalAmount) {
    }

    public record BatchView(
            UUID id, String batchNo, String name, String sourceSystem, MigrationStatus status,
            int itemCount, BigDecimal totalAmount, String remark, UUID createdBy,
            Instant createdAt, Instant importedAt, Instant rolledBackAt
    ) {
        public static BatchView from(MigrationBatch batch) {
            return new BatchView(batch.getId(), batch.getBatchNo(), batch.getName(), batch.getSourceSystem(),
                    batch.getStatus(), batch.getItemCount(), batch.getTotalAmount(), batch.getRemark(),
                    batch.getCreatedBy(), batch.getCreatedAt(), batch.getImportedAt(), batch.getRolledBackAt());
        }
    }

    public record ReconciliationView(
            UUID batchId, String batchNo, boolean ok, int itemCount,
            List<String> missingTargets, List<String> messages
    ) {
    }
}


