package com.ivs.billing.invoice.api;

import com.ivs.billing.invoice.InvoiceDirection;
import com.ivs.billing.invoice.InvoiceStatus;
import com.ivs.billing.invoice.InvoiceType;
import com.ivs.billing.invoice.SalesInvoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 票据模块对外契约。 */
public final class BillingApi {

    private BillingApi() {
    }

    public record InvoiceView(
            UUID id, String invoiceNo, InvoiceDirection direction, InvoiceType type, UUID orderId, String orderNo,
            UUID deliveryId, UUID customerId, String customerName, BigDecimal amount, BigDecimal taxRate,
            BigDecimal taxAmount, InvoiceStatus status, UUID originalInvoiceId, String externalInvoiceNo,
            Instant issuedAt, Instant createdAt, String remark
    ) {
        public static InvoiceView from(SalesInvoice invoice, String orderNo, String customerName) {
            return new InvoiceView(invoice.getId(), invoice.getInvoiceNo(), invoice.getDirection(), invoice.getType(),
                    invoice.getOrderId(), orderNo, invoice.getDeliveryId(), invoice.getCustomerId(), customerName,
                    invoice.getAmount(), invoice.getTaxRate(), invoice.getTaxAmount(), invoice.getStatus(),
                    invoice.getOriginalInvoiceId(), invoice.getExternalInvoiceNo(), invoice.getIssuedAt(),
                    invoice.getCreatedAt(), invoice.getRemark());
        }
    }

    public record CreateInvoiceCommand(
            UUID orderId, UUID deliveryId, UUID customerId, BigDecimal amount, BigDecimal taxRate, String remark
    ) {
    }

    public record IssueInvoiceCommand(String externalInvoiceNo, BigDecimal taxRate) {
    }
}