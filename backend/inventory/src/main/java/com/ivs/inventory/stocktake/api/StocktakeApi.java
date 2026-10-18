package com.ivs.inventory.stocktake.api;

import com.ivs.inventory.stocktake.Stocktake;
import com.ivs.inventory.stocktake.StocktakeLine;
import com.ivs.inventory.stocktake.StocktakeStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 库存盘点契约。 */
public final class StocktakeApi {

    private StocktakeApi() {
    }

    public record LineView(
            UUID id, UUID productId, String batchNo, BigDecimal bookQty, BigDecimal actualQty,
            BigDecimal differenceQty, BigDecimal unitCost, String differenceReason
    ) {
        public static LineView from(StocktakeLine line) {
            return new LineView(line.getId(), line.getProductId(), line.getBatchNo(), line.getBookQty(),
                    line.getActualQty(), line.getDifferenceQty(), line.getUnitCost(), line.getDifferenceReason());
        }
    }

    public record StocktakeView(
            UUID id, String stocktakeNo, UUID warehouseId, StocktakeStatus status, int lineCount,
            int differenceCount, BigDecimal totalDifferenceQty, String remark, UUID postedBy,
            Instant postedAt, UUID createdBy, Instant createdAt, List<LineView> lines
    ) {
        public static StocktakeView from(Stocktake stocktake, List<LineView> lines) {
            return new StocktakeView(stocktake.getId(), stocktake.getStocktakeNo(), stocktake.getWarehouseId(),
                    stocktake.getStatus(), stocktake.getLineCount(), stocktake.getDifferenceCount(),
                    stocktake.getTotalDifferenceQty(), stocktake.getRemark(), stocktake.getPostedBy(),
                    stocktake.getPostedAt(), stocktake.getCreatedBy(), stocktake.getCreatedAt(), lines);
        }
    }

    public record SaveLineCommand(
            UUID productId, String batchNo, BigDecimal actualQty, BigDecimal unitCost, String differenceReason
    ) {
    }

    public record CreateStocktakeCommand(
            UUID warehouseId, String remark, List<SaveLineCommand> lines
    ) {
    }

    public record UpdateStocktakeCommand(List<SaveLineCommand> lines) {
    }
}
