package com.ivs.billing.purchase;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 政策红字发票的 VIN/SN 明细。 */
@Entity
@Table(name = "purchase_invoice_rebate_line", schema = "ivs")
public class PurchaseInvoiceRebateLine {

    @Id private UUID id;
    @Column(name = "invoice_id", nullable = false) private UUID invoiceId;
    @Column(name = "claim_id") private UUID claimId;
    @Column(name = "claim_line_id") private UUID claimLineId;
    @Column(name = "entitlement_id") private UUID entitlementId;
    @Column(name = "serial_no", nullable = false, length = 60) private String serialNo;
    @Column(name = "purchase_order_id") private UUID purchaseOrderId;
    @Column(name = "purchase_receipt_id") private UUID purchaseReceiptId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected PurchaseInvoiceRebateLine() {
    }

    public static PurchaseInvoiceRebateLine create(UUID invoiceId, UUID claimId, UUID claimLineId,
                                                   UUID entitlementId, String serialNo, UUID purchaseOrderId,
                                                   UUID purchaseReceiptId, BigDecimal amount) {
        PurchaseInvoiceRebateLine line = new PurchaseInvoiceRebateLine();
        line.id = UUID.randomUUID();
        line.invoiceId = invoiceId;
        line.claimId = claimId;
        line.claimLineId = claimLineId;
        line.entitlementId = entitlementId;
        line.serialNo = serialNo == null ? null : serialNo.trim();
        line.purchaseOrderId = purchaseOrderId;
        line.purchaseReceiptId = purchaseReceiptId;
        line.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        line.createdAt = Instant.now();
        return line;
    }

    public UUID getId() { return id; }
    public UUID getInvoiceId() { return invoiceId; }
    public UUID getClaimId() { return claimId; }
    public UUID getClaimLineId() { return claimLineId; }
    public UUID getEntitlementId() { return entitlementId; }
    public String getSerialNo() { return serialNo; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public UUID getPurchaseReceiptId() { return purchaseReceiptId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
