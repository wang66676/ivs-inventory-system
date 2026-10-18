package com.ivs.sales.returns.api;

import com.ivs.sales.returns.SalesReturn;
import com.ivs.sales.returns.SalesReturnLine;
import com.ivs.sales.returns.SalesReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 销售退货契约。 */
public final class SalesReturnApi {

    private SalesReturnApi() {
    }

    public record LineView(
            UUID id, UUID productId, String productNo, String productName, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal amount, BigDecimal unitCost, BigDecimal costAmount,
            String batchNo, List<String> serialNos
    ) {
        public static LineView from(SalesReturnLine line, String productNo, String productName,
                                    List<String> serialNos) {
            return new LineView(line.getId(), line.getProductId(), productNo, productName, line.getQuantity(),
                    line.getUnitPrice(), line.getAmount(), line.getUnitCost(), line.getCostAmount(),
                    line.getBatchNo(), serialNos);
        }
    }

    public record ReturnView(
            UUID id, String returnNo, UUID customerId, String customerName, UUID deliveryId, UUID warehouseId,
            String warehouseName, SalesReturnStatus status, BigDecimal totalAmount, BigDecimal costAmount,
            BigDecimal receivableAdjustmentAmount, BigDecimal refundAmount, String reason, String remark,
            UUID postedBy, Instant postedAt, UUID createdBy, Instant createdAt, List<LineView> lines
    ) {
        public static ReturnView from(SalesReturn value, String customerName, String warehouseName,
                                      List<LineView> lines) {
            return new ReturnView(value.getId(), value.getReturnNo(), value.getCustomerId(), customerName,
                    value.getDeliveryId(), value.getWarehouseId(), warehouseName, value.getStatus(),
                    value.getTotalAmount(), value.getCostAmount(), value.getReceivableAdjustmentAmount(),
                    value.getRefundAmount(), value.getReason(), value.getRemark(), value.getPostedBy(),
                    value.getPostedAt(), value.getCreatedBy(), value.getCreatedAt(), lines);
        }
    }

    public record SaveLineCommand(
            UUID productId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal unitCost,
            String batchNo, List<String> serialNos
    ) {
    }

    public record CreateReturnCommand(
            UUID customerId, UUID deliveryId, UUID warehouseId, String reason, String remark,
            List<SaveLineCommand> lines
    ) {
    }
}
