package com.ivs.sales.order.api;

import com.ivs.sales.order.DeliveryStatus;
import com.ivs.sales.order.SalesDelivery;
import com.ivs.sales.order.SalesOrder;
import com.ivs.sales.order.SalesOrderStatus;
import com.ivs.sales.settlement.PaymentMethod;
import com.ivs.sales.settlement.Receipt;
import com.ivs.sales.settlement.Receivable;
import com.ivs.sales.settlement.ReceivableStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 销售与结算模块对外契约。 */
public final class SalesApi {

    private SalesApi() {
    }

    public record SalesOrderView(
            UUID id, String orderNo, String externalOrderNo, UUID customerId, String customerName, String customerType,
            UUID warehouseId, String warehouseName, SalesOrderStatus status, String businessScope, BigDecimal totalAmount,
            boolean priceApprovalRequired, boolean priceApproved, Instant lockedAt, String remark, Instant createdAt,
            String paymentMode, BigDecimal downPayment, int installmentCount, LocalDate firstDueDate,
            List<SalesOrderLineView> lines
    ) {
        public static SalesOrderView from(SalesOrder order, String customerName, String customerType,
                                          String warehouseName, String businessScope, List<SalesOrderLineView> lines) {
            return new SalesOrderView(order.getId(), order.getOrderNo(), order.getExternalOrderNo(),
                    order.getCustomerId(), customerName, customerType, order.getWarehouseId(), warehouseName,
                    order.getStatus(), businessScope, order.getTotalAmount(),
                    order.isPriceApprovalRequired(), order.getPriceApprovedBy() != null, order.getLockedAt(),
                    order.getRemark(), order.getCreatedAt(), order.getPaymentMode().name(), order.getDownPayment(),
                    order.getInstallmentCount(), order.getFirstDueDate(), lines);
        }
    }

    public record SalesOrderLineView(
            UUID id, int lineNo, UUID productId, String productNo, String productName, String categoryCode,
            String categoryName, String trackingMode, BigDecimal quantity, BigDecimal deliveredQuantity,
            BigDecimal remainingQuantity, BigDecimal unitPrice, BigDecimal minPrice, BigDecimal taxRate,
            BigDecimal amount, String batteryBoxManufacturer, String batteryBoxModel,
            BigDecimal batteryBoxUnitPrice, String batteryPackManufacturer, BigDecimal batteryEnergyKwh,
            BigDecimal batteryPackUnitPrice, boolean belowMinPrice,
            List<UUID> serialIds, List<UUID> remainingSerialIds, String remark
    ) {
    }

    public record SaveSalesOrderCommand(
            String externalOrderNo, UUID customerId, UUID warehouseId, String remark,
            List<SaveSalesOrderLineCommand> lines, String paymentMode, BigDecimal downPayment,
            int installmentCount, LocalDate firstDueDate
    ) {
        public SaveSalesOrderCommand(String externalOrderNo, UUID customerId, UUID warehouseId, String remark,
                                     List<SaveSalesOrderLineCommand> lines) {
            this(externalOrderNo, customerId, warehouseId, remark, lines, "CASH", BigDecimal.ZERO, 0, null);
        }
    }

    public record SaveSalesOrderLineCommand(
            UUID productId, BigDecimal quantity, BigDecimal unitPrice, List<UUID> serialIds, String remark,
            String batteryBoxManufacturer, String batteryBoxModel, BigDecimal batteryBoxUnitPrice,
            String batteryPackManufacturer, BigDecimal batteryEnergyKwh, BigDecimal batteryPackUnitPrice
    ) {
        /** 兼容旧调用：整车与普通商品不填写电池分项信息。 */
        public SaveSalesOrderLineCommand(UUID productId, BigDecimal quantity, BigDecimal unitPrice,
                                         List<UUID> serialIds, String remark) {
            this(productId, quantity, unitPrice, serialIds, remark,
                    null, null, null, null, null, null);
        }
    }

    public record DeliverLineCommand(UUID orderLineId, BigDecimal quantity, List<UUID> serialIds) {
    }

    public record UpdatePriceLineCommand(UUID orderLineId, BigDecimal unitPrice,
                                         BigDecimal batteryBoxUnitPrice, BigDecimal batteryPackUnitPrice) {
        public UpdatePriceLineCommand(UUID orderLineId, BigDecimal unitPrice) {
            this(orderLineId, unitPrice, null, null);
        }
    }

    public record UpdatePricesCommand(List<UpdatePriceLineCommand> lines) {
    }

    public record DeliverCommand(UUID warehouseId, String remark, List<DeliverLineCommand> lines) {
    }

    public record SalesDeliveryView(
            UUID id, String deliveryNo, UUID orderId, String orderNo, UUID customerId, String customerName,
            UUID warehouseId, String warehouseName, DeliveryStatus status, String businessScope, Instant deliveredAt,
            BigDecimal amount, BigDecimal costAmount, BigDecimal grossProfit, String remark,
            List<SalesDeliveryLineView> lines
    ) {
        public static SalesDeliveryView from(SalesDelivery delivery, String orderNo, String customerName,
                                             String warehouseName, String businessScope,
                                             List<SalesDeliveryLineView> lines) {
            return new SalesDeliveryView(delivery.getId(), delivery.getDeliveryNo(), delivery.getOrderId(),
                    orderNo, delivery.getCustomerId(), customerName, delivery.getWarehouseId(), warehouseName,
                    delivery.getStatus(), businessScope, delivery.getDeliveredAt(), delivery.getAmount(), delivery.getCostAmount(),
                    delivery.grossProfit(), delivery.getRemark(), lines);
        }
    }

    public record SalesDeliveryLineView(
            UUID id, UUID productId, String productNo, String productName, BigDecimal quantity,
            BigDecimal unitPrice, BigDecimal costAmount, List<UUID> serialIds
    ) {
    }

    public record ReceivableView(
            UUID id, String receivableNo, UUID customerId, String customerName, UUID orderId, String orderNo,
            UUID deliveryId, BigDecimal amount, BigDecimal receivedAmount, BigDecimal outstanding,
            ReceivableStatus status, LocalDate dueDate, Instant createdAt
    ) {
        public static ReceivableView from(Receivable receivable, String customerName, String orderNo) {
            return new ReceivableView(receivable.getId(), receivable.getReceivableNo(), receivable.getCustomerId(),
                    customerName, receivable.getOrderId(), orderNo, receivable.getDeliveryId(),
                    receivable.getAmount(), receivable.getReceivedAmount(), receivable.outstanding(),
                    receivable.getStatus(), receivable.getDueDate(), receivable.getCreatedAt());
        }
    }

    public record CreateReceiptCommand(
            UUID customerId, BigDecimal amount, PaymentMethod method, String remark,
            LocalDate receivedAt, List<UUID> receivableIds
    ) {
        public CreateReceiptCommand(UUID customerId, BigDecimal amount, PaymentMethod method, String remark) {
            this(customerId, amount, method, remark, null, null);
        }

        public CreateReceiptCommand(UUID customerId, BigDecimal amount, PaymentMethod method, String remark,
                                    List<UUID> receivableIds) {
            this(customerId, amount, method, remark, null, receivableIds);
        }
    }

    public record InstallmentScheduleView(
            UUID id, int periodNo, UUID receivableId, String receivableNo, LocalDate dueDate, BigDecimal amount,
            BigDecimal receivedAmount, BigDecimal outstanding, ReceivableStatus status, long overdueDays
    ) {
    }

    public record InstallmentPlanView(
            UUID id, String planNo, UUID orderId, String orderNo, UUID customerId, String customerName,
            BigDecimal totalAmount, BigDecimal downPayment, BigDecimal financedAmount, int installmentCount,
            LocalDate firstDueDate, String status, BigDecimal receivedAmount, BigDecimal outstanding,
            BigDecimal overdueAmount, LocalDate nextDueDate, Instant createdAt, List<InstallmentScheduleView> schedules
    ) {
    }

    public record ReceiptAllocationView(UUID receivableId, String receivableNo, String orderNo, BigDecimal amount) {
    }

    public record ReceiptView(
            UUID id, String receiptNo, UUID customerId, String customerName, BigDecimal amount,
            PaymentMethod method, Instant receivedAt, String remark, List<ReceiptAllocationView> allocations
    ) {
        public static ReceiptView from(Receipt receipt, String customerName, List<ReceiptAllocationView> allocations) {
            return new ReceiptView(receipt.getId(), receipt.getReceiptNo(), receipt.getCustomerId(), customerName,
                    receipt.getAmount(), receipt.getMethod(), receipt.getReceivedAt(), receipt.getRemark(),
                    allocations);
        }
    }

    /** 应收账龄分档。 */
    public record AgingBucketView(String label, int count, BigDecimal amount) {
    }
}
