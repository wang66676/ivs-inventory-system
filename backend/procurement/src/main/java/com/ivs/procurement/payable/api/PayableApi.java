package com.ivs.procurement.payable.api;

import com.ivs.procurement.payable.Payable;
import com.ivs.procurement.payable.PayableStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PayableApi {
    private PayableApi() {}

    public record PayableView(
            UUID id, String payableNo, UUID supplierId, String supplierName, UUID purchaseOrderId,
            UUID purchaseReceiptId, String orderNo, String receiptNo, BigDecimal purchaseQuantity, BigDecimal amount,
            BigDecimal invoiceMatchedAmount, BigDecimal uninvoicedAmount, BigDecimal paidAmount,
            BigDecimal outstanding, LocalDate dueDate, LocalDate lastPaymentDate, PayableStatus status,
            String remark, Instant createdAt
    ) {
        public static PayableView from(Payable p, String supplierName) {
            return new PayableView(p.getId(), p.getPayableNo(), p.getSupplierId(), supplierName,
                    p.getPurchaseOrderId(), p.getPurchaseReceiptId(), p.getOrderNo(), p.getReceiptNo(),
                    p.getPurchaseQuantity(), p.getAmount(), p.getInvoiceMatchedAmount(), p.getUninvoicedAmount(), p.getPaidAmount(),
                    p.outstanding(), p.getDueDate(), p.getLastPaymentDate(), p.getStatus(),
                    p.getRemark(), p.getCreatedAt());
        }
    }

    public record PayCommand(BigDecimal amount, String remark, LocalDate paymentDate) {}
}
