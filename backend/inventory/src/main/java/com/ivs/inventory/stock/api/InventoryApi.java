package com.ivs.inventory.stock.api;

import com.ivs.inventory.stock.StockDirection;
import com.ivs.inventory.stock.StockLedger;
import com.ivs.inventory.stock.StockLock;
import com.ivs.inventory.stock.StockLockStatus;
import com.ivs.inventory.stock.StockMoveType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 库存模块对外契约。商品名称、仓库名称等主数据字段由 app 层装配填充
 * （业务模块之间不允许直接依赖），模块内部只填自己拥有的字段。
 */
public final class InventoryApi {

    private InventoryApi() {
    }

    public record BalanceView(
            UUID id, UUID productId, String productNo, String productName, String categoryCode,
            String trackingMode, UUID warehouseId, String warehouseName, String batchNo,
            BigDecimal onHandQty, BigDecimal lockedQty, BigDecimal availableQty,
            BigDecimal unitCost, BigDecimal amount, Instant updatedAt,
            UUID purchaseOrderId, String purchaseOrderNo
    ) {
    }

    public record LedgerView(
            UUID id, UUID productId, String productNo, String productName, UUID serialId, String serialNo,
            UUID warehouseId, String warehouseName, StockMoveType moveType, StockDirection direction,
            BigDecimal quantity, BigDecimal unitCost, BigDecimal amount, BigDecimal balanceAfter,
            String bizType, String bizNo, Instant occurredAt, String remark
    ) {
        public static LedgerView from(StockLedger ledger) {
            return new LedgerView(ledger.getId(), ledger.getProductId(), null, null, ledger.getSerialId(), null,
                    ledger.getWarehouseId(), null, ledger.getMoveType(), ledger.getDirection(),
                    ledger.getQuantity(), ledger.getUnitCost(), ledger.getAmount(), ledger.getBalanceAfter(),
                    ledger.getBizType(), ledger.getBizNo(), ledger.getOccurredAt(), ledger.getRemark());
        }
    }

    public record AgeBucketView(String label, int serialCount, BigDecimal amount) {
    }

    public record CategorySummaryView(String categoryName, int itemCount, BigDecimal amount) {
    }

    public record WarehouseSummaryView(String warehouseName, int itemCount, BigDecimal amount) {
    }

    /** 车系 VIN 库存：未销售只统计在库未锁定；已锁定 VIN 视为已销售，仅单独备注。 */
    public record VehicleSeriesInventoryView(String series, int unsoldVinCount, int lockedVinCount) {
    }

    public record SummaryView(
            int serialInStock, int vehicleInStockNotLocked, int batteryInStock,
            int quantityItems, BigDecimal totalAmount, BigDecimal totalLockedQty,
            List<CategorySummaryView> byCategory, List<WarehouseSummaryView> byWarehouse,
            List<AgeBucketView> ageBuckets, List<VehicleSeriesInventoryView> vehicleSeriesInventory,
            long ledgerCount, boolean consistent
    ) {
    }

    public record LockView(
            UUID id, UUID productId, String productNo, String productName, UUID serialId, String serialNo,
            UUID warehouseId, String warehouseName, BigDecimal quantity, StockLockStatus status,
            String bizType, String bizNo, Instant expiresAt, Instant createdAt
    ) {
        public static LockView from(StockLock lock) {
            return new LockView(lock.getId(), lock.getProductId(), null, null, lock.getSerialId(), null,
                    lock.getWarehouseId(), null, lock.getQuantity(), lock.getStatus(), lock.getBizType(),
                    lock.getBizNo(), lock.getExpiresAt(), lock.getCreatedAt());
        }
    }

    public record CreateLockCommand(
            UUID productId, UUID serialId, UUID warehouseId, String batchNo, BigDecimal quantity,
            String bizType, UUID bizId, String bizNo, Integer validDays
    ) {
    }

    public record AdjustStockCommand(
            UUID productId, UUID warehouseId, String batchNo, BigDecimal targetQuantity,
            BigDecimal targetUnitCost, String remark
    ) {
    }

    public record DifferenceView(
            UUID productId, String productNo, UUID warehouseId, String warehouseName,
            String batchNo, BigDecimal balanceQty, BigDecimal ledgerQty
    ) {
    }

    public record ReconciliationView(boolean consistent, long ledgerCount, List<DifferenceView> differences) {
    }
}
