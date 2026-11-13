package com.ivs.procurement.purchase.api;

import com.ivs.procurement.purchase.PurchaseOrder;
import com.ivs.procurement.purchase.PurchaseOrderStatus;
import com.ivs.procurement.purchase.PurchaseReceipt;
import com.ivs.procurement.purchase.ReceiptStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 采购模块对外契约。 */
public final class ProcurementApi {

    private ProcurementApi() {
    }

    public record PurchaseOrderView(
            UUID id, String orderNo, String externalOrderNo, UUID supplierId, String supplierName, String supplierType,
            UUID warehouseId, String warehouseName, PurchaseOrderStatus status, String businessScope,
            BigDecimal totalAmount,
            LocalDate expectedDate, String remark, Instant createdAt, List<PurchaseOrderLineView> lines
    ) {
        public static PurchaseOrderView from(PurchaseOrder order, SupplierInfoView supplier, WarehouseInfoView warehouse,
                                             String businessScope, List<PurchaseOrderLineView> lines) {
            return new PurchaseOrderView(order.getId(), order.getOrderNo(), order.getExternalOrderNo(), order.getSupplierId(),
                    supplier == null ? null : supplier.name(), supplier == null ? null : supplier.type(),
                    order.getWarehouseId(), warehouse == null ? null : warehouse.name(), order.getStatus(), businessScope,
                    order.getTotalAmount(), order.getExpectedDate(), order.getRemark(), order.getCreatedAt(), lines);
        }
    }

    public record PurchaseOrderLineView(
            UUID id, int lineNo, UUID productId, String productNo, String productName, String series, String configCode,
            String configText, String serialNo, String cabType, LocalDate certificateApplicationDate,
            LocalDate arrivalDate,
            UUID warehouseId, String warehouseName,
            String categoryCode, String categoryName, String trackingMode, BigDecimal quantity, BigDecimal receivedQuantity,
            BigDecimal remainingQuantity, BigDecimal unitPrice, BigDecimal batteryBoxUnitPrice,
            String batteryBoxManufacturer, String batteryBoxModel, String batteryPackManufacturer,
            BigDecimal batteryEnergyKwh, BigDecimal batteryPackUnitPrice, BigDecimal taxRate, BigDecimal amount,
            String remark, String lineStatus, List<PolicyRefView> policies
    ) {
    }

    public record PolicyRefView(UUID id, String policyNo, String name) {
    }

    public record SupplierInfoView(UUID id, String name, String type) {
    }

    public record WarehouseInfoView(UUID id, String name) {
    }

    public record SavePurchaseOrderCommand(
            UUID supplierId, UUID warehouseId, LocalDate expectedDate, String remark,
            List<SavePurchaseOrderLineCommand> lines, String externalOrderNo
    ) {
        /** 兼容旧调用：不填写厂家订单编号。 */
        public SavePurchaseOrderCommand(UUID supplierId, UUID warehouseId, LocalDate expectedDate, String remark,
                                        List<SavePurchaseOrderLineCommand> lines) {
            this(supplierId, warehouseId, expectedDate, remark, lines, null);
        }
    }

    public record SavePurchaseOrderLineCommand(
            UUID productId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal taxRate, String remark,
            List<UUID> policyIds, String configText, UUID warehouseId, String serialNo,
            LocalDate certificateApplicationDate, String batteryBoxManufacturer, String batteryBoxModel,
            BigDecimal batteryBoxUnitPrice, String batteryPackManufacturer, BigDecimal batteryEnergyKwh,
            BigDecimal batteryPackUnitPrice, LocalDate arrivalDate
    ) {
        /** 兼容旧调用：不关联返利政策、配置文本和明细仓库。 */
        public SavePurchaseOrderLineCommand(UUID productId, BigDecimal quantity, BigDecimal unitPrice,
                                            BigDecimal taxRate, String remark) {
            this(productId, quantity, unitPrice, taxRate, remark, List.of(), null, null, null, null,
                    null, null, null, null, null, null, null);
        }

        public SavePurchaseOrderLineCommand(UUID productId, BigDecimal quantity, BigDecimal unitPrice,
                                            BigDecimal taxRate, String remark, List<UUID> policyIds) {
            this(productId, quantity, unitPrice, taxRate, remark, policyIds, null, null, null, null,
                    null, null, null, null, null, null, null);
        }
    }

    public record ReceiveLineCommand(
            UUID orderLineId, BigDecimal quantity, List<String> serialNos, String batchNo, BigDecimal unitPrice,
            UUID warehouseId, LocalDate certificateApplicationDate
    ) {
        public ReceiveLineCommand(UUID orderLineId, BigDecimal quantity, List<String> serialNos, String batchNo,
                                  BigDecimal unitPrice) {
            this(orderLineId, quantity, serialNos, batchNo, unitPrice, null, null);
        }
    }

    public record ReceivePurchaseOrderCommand(
            UUID warehouseId, String remark, List<ReceiveLineCommand> lines,
            /** 入库单确认时勾选的返利入池记录（可空；只能使用同一厂家的已到账返利） */
            List<UUID> offsetTxnIds
    ) {
    }

    public record PurchaseReceiptView(
            UUID id, String receiptNo, UUID orderId, String orderNo, UUID supplierId, String supplierName,
            UUID warehouseId, String warehouseName, ReceiptStatus status, String businessScope,
            Instant receivedAt, BigDecimal amount,
            BigDecimal offsetAmount, BigDecimal netAmount, String remark, List<PurchaseReceiptLineView> lines
    ) {
        public static PurchaseReceiptView from(PurchaseReceipt receipt, String orderNo, String supplierName,
                                              String warehouseName, String businessScope,
                                              List<PurchaseReceiptLineView> lines) {
            return new PurchaseReceiptView(receipt.getId(), receipt.getReceiptNo(), receipt.getOrderId(), orderNo,
                    receipt.getSupplierId(), supplierName, receipt.getWarehouseId(), warehouseName,
                    receipt.getStatus(), businessScope, receipt.getReceivedAt(), receipt.getAmount(),
                    receipt.getOffsetAmount(), receipt.netAmount(), receipt.getRemark(), lines);
        }
    }

    public record PurchaseReceiptLineView(
            UUID id, UUID productId, String productNo, String productName, String series, String configText,
            UUID warehouseId, String warehouseName, String cabType, BigDecimal quantity, BigDecimal unitPrice,
            String batchNo, List<String> serialNos,
            LocalDate certificateApplicationDate, String batteryBoxManufacturer, String batteryBoxModel,
            BigDecimal batteryBoxUnitPrice, String batteryPackManufacturer, BigDecimal batteryEnergyKwh,
            BigDecimal batteryPackUnitPrice
    ) {
    }

    public record UpdateCertificateDateCommand(LocalDate certificateApplicationDate) {
    }

    public record UpdateReceiptWarehouseCommand(UUID warehouseId) {
    }

    public record ReturnReceiptCommand(String reason) {
    }

    public record PurchaseOrderLinkView(UUID orderId, String orderNo, String externalOrderNo,
                                        BigDecimal purchasePrice) {
    }
}
