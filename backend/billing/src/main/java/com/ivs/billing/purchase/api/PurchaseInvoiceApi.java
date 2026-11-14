package com.ivs.billing.purchase.api;

import com.ivs.billing.purchase.PurchaseInvoice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class PurchaseInvoiceApi {

    private PurchaseInvoiceApi() {
    }

    public record PurchaseInvoiceView(
            UUID id, String invoiceNo, String direction, String type, String invoiceType, UUID supplierId, UUID purchaseOrderId,
            java.util.List<String> serialNos, BigDecimal amount,
            BigDecimal matchedAmount, BigDecimal unmatchedAmount, BigDecimal taxRate, BigDecimal taxAmount,
            String status, String originalInvoiceNo,
            String externalInvoiceNo, UUID claimId, Instant issuedAt, Instant createdAt,
            String pdfFileName, String extractedText, String remark
    ) {
        public static PurchaseInvoiceView from(PurchaseInvoice invoice) {
            return new PurchaseInvoiceView(invoice.getId(), invoice.getInvoiceNo(), invoice.getDirection(),
                    invoice.getType(), invoice.getInvoiceType(), invoice.getSupplierId(), invoice.getPurchaseOrderId(),
                    invoice.getSerialNos() == null ? java.util.List.of()
                            : java.util.Arrays.stream(invoice.getSerialNos().split(","))
                            .map(String::trim).filter(value -> !value.isBlank()).toList(),
                    invoice.getAmount(),
                    invoice.getMatchedAmount(), invoice.getUnmatchedAmount(), invoice.getTaxRate(),
                    invoice.getTaxAmount(), invoice.getStatus(), invoice.getOriginalInvoiceNo(),
                    invoice.getExternalInvoiceNo(), invoice.getClaimId(), invoice.getIssuedAt(),
                    invoice.getCreatedAt(), invoice.getPdfFileName(), invoice.getExtractedText(), invoice.getRemark());
        }
    }

    public record RegisterRedInvoiceCommand(
            UUID supplierId, BigDecimal amount, BigDecimal taxRate, String originalInvoiceNo,
            String externalInvoiceNo, UUID claimId, Instant issuedAt,
            String pdfFileName, String pdfStorageKey, String extractedText, String remark
    ) {
    }

    public record RegisterBlueInvoiceCommand(
            UUID supplierId, UUID purchaseOrderId, java.util.List<String> serialNos,
            BigDecimal amount, BigDecimal taxRate, String invoiceType, String externalInvoiceNo,
            Instant issuedAt, String pdfFileName, String pdfStorageKey, String extractedText, String remark
    ) {
    }

    public record PdfExtractionView(
            String storageKey, String fileName, String rawText, String invoiceNo, String invoiceType, String type,
            BigDecimal amount, BigDecimal taxAmount, BigDecimal taxRate, Instant issuedAt,
            String sellerName, String sellerTaxNo, String buyerName, String buyerTaxNo,
            java.util.List<String> vinCandidates, java.util.List<String> orderNoCandidates,
            int confidence, boolean textExtracted, String warning
    ) {
    }
}
