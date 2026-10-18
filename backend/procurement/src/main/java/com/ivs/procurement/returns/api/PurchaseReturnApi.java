package com.ivs.procurement.returns.api;

import com.ivs.procurement.returns.PurchaseReturn;
import com.ivs.procurement.returns.PurchaseReturnLine;
import com.ivs.procurement.returns.PurchaseReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 采购退货契约。 */
public final class PurchaseReturnApi {

    private PurchaseReturnApi() {
    }

    public record LineView(
            UUID id, UUID productId, String productNo, String productName, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal amount, String batchNo, List<String> serialNos
    ) {
        public static LineView from(PurchaseReturnLine line, String productNo, String productName,
                                    List<String> serialNos) {
            return new LineView(line.getId(), line.getProductId(), productNo, productName, line.getQuantity(),
                    line.getUnitPrice(), line.getAmount(), line.getBatchNo(), serialNos);
        }
    }

    public record ReturnView(
            UUID id, String returnNo, UUID supplierId, String supplierName, UUID warehouseId, String warehouseName,
            PurchaseReturnStatus status, BigDecimal totalAmount, String reason, String remark,
            UUID postedBy, Instant postedAt, UUID createdBy, Instant createdAt, List<LineView> lines
    ) {
        public static ReturnView from(PurchaseReturn value, String supplierName, String warehouseName,
                                      List<LineView> lines) {
            return new ReturnView(value.getId(), value.getReturnNo(), value.getSupplierId(), supplierName,
                    value.getWarehouseId(), warehouseName, value.getStatus(), value.getTotalAmount(),
                    value.getReason(), value.getRemark(), value.getPostedBy(), value.getPostedAt(),
                    value.getCreatedBy(), value.getCreatedAt(), lines);
        }
    }

    public record SaveLineCommand(
            UUID productId, BigDecimal quantity, BigDecimal unitPrice, String batchNo, List<String> serialNos
    ) {
    }

    public record CreateReturnCommand(
            UUID supplierId, UUID warehouseId, String reason, String remark, List<SaveLineCommand> lines
    ) {
    }
}
