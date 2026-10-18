package com.ivs.inventory.transfer.api;

import com.ivs.inventory.transfer.StockTransfer;
import com.ivs.inventory.transfer.StockTransferLine;
import com.ivs.inventory.transfer.StockTransferStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 库存调拨契约。 */
public final class StockTransferApi {

    private StockTransferApi() {
    }

    public record LineView(
            UUID id, UUID productId, String batchNo, BigDecimal quantity, BigDecimal receivedQuantity,
            BigDecimal differenceQuantity, BigDecimal unitCost, String differenceReason
    ) {
        public static LineView from(StockTransferLine line) {
            return new LineView(line.getId(), line.getProductId(), line.getBatchNo(), line.getQuantity(),
                    line.getReceivedQuantity(), line.getDifferenceQuantity(), line.getUnitCost(),
                    line.getDifferenceReason());
        }
    }

    public record TransferView(
            UUID id, String transferNo, UUID fromWarehouseId, UUID toWarehouseId, StockTransferStatus status,
            int lineCount, BigDecimal totalQuantity, BigDecimal receivedQuantity, BigDecimal differenceQuantity,
            String remark, UUID approvedBy, Instant approvedAt, UUID dispatchedBy, Instant dispatchedAt,
            UUID receivedBy, Instant receivedAt, Instant cancelledAt, UUID createdBy, Instant createdAt,
            List<LineView> lines
    ) {
        public static TransferView from(StockTransfer transfer, List<LineView> lines) {
            return new TransferView(transfer.getId(), transfer.getTransferNo(), transfer.getFromWarehouseId(),
                    transfer.getToWarehouseId(), transfer.getStatus(), transfer.getLineCount(),
                    transfer.getTotalQuantity(), transfer.getReceivedQuantity(), transfer.getDifferenceQuantity(),
                    transfer.getRemark(), transfer.getApprovedBy(), transfer.getApprovedAt(),
                    transfer.getDispatchedBy(), transfer.getDispatchedAt(), transfer.getReceivedBy(),
                    transfer.getReceivedAt(), transfer.getCancelledAt(), transfer.getCreatedBy(),
                    transfer.getCreatedAt(), lines);
        }
    }

    public record SaveLineCommand(
            UUID productId, String batchNo, BigDecimal quantity, BigDecimal unitCost
    ) {
    }

    public record CreateTransferCommand(
            UUID fromWarehouseId, UUID toWarehouseId, String remark, List<SaveLineCommand> lines
    ) {
    }

    public record UpdateTransferCommand(List<SaveLineCommand> lines) {
    }

    public record ReceiveLineCommand(UUID lineId, BigDecimal receivedQuantity, String differenceReason) {
    }

    public record ReceiveTransferCommand(List<ReceiveLineCommand> lines) {
    }
}
