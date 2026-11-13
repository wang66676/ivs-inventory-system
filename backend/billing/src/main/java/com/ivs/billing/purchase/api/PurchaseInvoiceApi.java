package com.ivs.billing.purchase.api;

import com.ivs.billing.purchase.PurchaseInvoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PurchaseInvoiceApi {

    private PurchaseInvoiceApi() {
    }

    public record PurchaseInvoiceView(
            UUID id, String invoiceNo, String direction, String type, UUID supplierId, UUID purchaseOrderId,
            java.util.List<String> serialNos, BigDecimal amount,
            BigDecimal matchedAmount, BigDecimal unmatchedAmount, BigDecimal taxRate, BigDecimal taxAmount,
            String status, String originalInvoiceNo,
            String externalInvoiceNo, UUID claimId, Instant issuedAt, Instant createdAt, String remark
    ) {
        public static PurchaseInvoiceView from(PurchaseInvoice invoice) {
            return new PurchaseInvoiceView(invoice.getId(), invoice.getInvoiceNo(), invoice.getDirection(),
                    invoice.getType(), invoice.getSupplierId(), invoice.getPurchaseOrderId(),
                    invoice.getSerialNos() == null ? java.util.List.of()
                            : java.util.Arrays.stream(invoice.getSerialNos().split(","))
                            .map(String::trim).filter(value -> !value.isBlank()).toList(),
                    invoice.getAmount(),
                    invoice.getMatchedAmount(), invoice.getUnmatchedAmount(), invoice.getTaxRate(),
                    invoice.getTaxAmount(), invoice.getStatus(), invoice.getOriginalInvoiceNo(),
                    invoice.getExternalInvoiceNo(), invoice.getClaimId(), invoice.getIssuedAt(),
                    invoice.getCreatedAt(), invoice.getRemark());
        }
    }

    public record RegisterRedInvoiceCommand(
            UUID supplierId, BigDecimal amount, BigDecimal taxRate, String originalInvoiceNo,
            String externalInvoiceNo, UUID claimId, String remark
    ) {
    }

    public record RegisterBlueInvoiceCommand(
            UUID supplierId, UUID purchaseOrderId, java.util.List<String> serialNos,
            BigDecimal amount, BigDecimal taxRate, String externalInvoiceNo,
            Instant issuedAt, String remark
    ) {
    }
}
