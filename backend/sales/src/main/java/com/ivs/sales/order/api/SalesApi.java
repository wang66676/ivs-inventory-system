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
            UUID warehouseId, String warehouseName, SalesOrderStatus status, BigDecimal totalAmount,
            boolean priceApprovalRequired, boolean priceApproved, Instant lockedAt, String remark, Instant createdAt,
            List<SalesOrderLineView> lines
    ) {
        public static SalesOrderView from(SalesOrder order, String customerName, String customerType,
                                          String warehouseName, List<SalesOrderLineView> lines) {
            return new SalesOrderView(order.getId(), order.getOrderNo(), order.getExternalOrderNo(),
                    order.getCustomerId(), customerName, customerType, order.getWarehouseId(), warehouseName,
                    order.getStatus(), order.getTotalAmount(),
                    order.isPriceApprovalRequired(), order.getPriceApprovedBy() != null, order.getLockedAt(),
                    order.getRemark(), order.getCreatedAt(), lines);
        }
    }

    public record SalesOrderLineView(
            UUID id, int lineNo, UUID productId, String productNo, String productName, String categoryCode,
            String trackingMode, BigDecimal quantity, BigDecimal deliveredQuantity, BigDecimal remainingQuantity,
            BigDecimal unitPrice, BigDecimal minPrice, BigDecimal taxRate, BigDecimal amount, boolean belowMinPrice,
            List<UUID> serialIds, List<UUID> remainingSerialIds, String remark
    ) {
    }

    public record SaveSalesOrderCommand(
            String externalOrderNo, UUID customerId, UUID warehouseId, String remark,
            List<SaveSalesOrderLineCommand> lines
    ) {
    }

    public record SaveSalesOrderLineCommand(
            UUID productId, BigDecimal quantity, BigDecimal unitPrice, List<UUID> serialIds, String remark
    ) {
    }

    public record DeliverLineCommand(UUID orderLineId, BigDecimal quantity, List<UUID> serialIds) {
    }

    public record UpdatePriceLineCommand(UUID orderLineId, BigDecimal unitPrice) {
    }

    public record UpdatePricesCommand(List<UpdatePriceLineCommand> lines) {
    }

    public record DeliverCommand(UUID warehouseId, String remark, List<DeliverLineCommand> lines) {
    }

    public record SalesDeliveryView(
            UUID id, String deliveryNo, UUID orderId, String orderNo, UUID customerId, String customerName,
            UUID warehouseId, String warehouseName, DeliveryStatus status, Instant deliveredAt,
            BigDecimal amount, BigDecimal costAmount, BigDecimal grossProfit, String remark,
            List<SalesDeliveryLineView> lines
    ) {
        public static SalesDeliveryView from(SalesDelivery delivery, String orderNo, String customerName,
                                             String warehouseName, List<SalesDeliveryLineView> lines) {
            return new SalesDeliveryView(delivery.getId(), delivery.getDeliveryNo(), delivery.getOrderId(),
                    orderNo, delivery.getCustomerId(), customerName, delivery.getWarehouseId(), warehouseName,
                    delivery.getStatus(), delivery.getDeliveredAt(), delivery.getAmount(), delivery.getCostAmount(),
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
            UUID customerId, BigDecimal amount, PaymentMethod method, String remark
    ) {
    }

    public record ReceiptAllocationView(UUID receivableId, String receivableNo, BigDecimal amount) {
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
