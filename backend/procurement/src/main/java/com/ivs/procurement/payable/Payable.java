package com.ivs.procurement.payable;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 供应商应付：采购收货生成，付款核销，采购退货冲减。 */
@Entity
@Table(name = "payable", schema = "ivs")
public class Payable {

    @Id private UUID id;
    @Column(name = "payable_no", nullable = false, unique = true, length = 40) private String payableNo;
    @Column(name = "supplier_id", nullable = false) private UUID supplierId;
    @Column(name = "purchase_order_id") private UUID purchaseOrderId;
    @Column(name = "purchase_receipt_id", unique = true) private UUID purchaseReceiptId;
    @Column(name = "order_no", length = 40) private String orderNo;
    @Column(name = "receipt_no", length = 40) private String receiptNo;
    @Column(name = "purchase_quantity", nullable = false, precision = 18, scale = 3) private BigDecimal purchaseQuantity;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "paid_amount", nullable = false, precision = 18, scale = 2) private BigDecimal paidAmount;
    @Column(name = "invoice_matched_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal invoiceMatchedAmount;
    @Column(name = "due_date") private LocalDate dueDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PayableStatus status;
    @Column(length = 300) private String remark;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected Payable() {
    }

    public static Payable fromReceipt(String payableNo, UUID supplierId, UUID orderId, UUID receiptId,
                                      String orderNo, String receiptNo, BigDecimal purchaseQuantity,
                                      BigDecimal amount, LocalDate dueDate) {
        Payable value = new Payable();
        value.id = UUID.randomUUID();
        value.payableNo = payableNo;
        value.supplierId = supplierId;
        value.purchaseOrderId = orderId;
        value.purchaseReceiptId = receiptId;
        value.orderNo = orderNo;
        value.receiptNo = receiptNo;
        value.purchaseQuantity = purchaseQuantity == null ? BigDecimal.ZERO : purchaseQuantity;
        value.amount = money(amount);
        value.paidAmount = money(BigDecimal.ZERO);
        value.invoiceMatchedAmount = money(BigDecimal.ZERO);
        value.dueDate = dueDate;
        value.status = PayableStatus.OPEN;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public static Payable importOpening(String payableNo, UUID supplierId, BigDecimal amount,
                                        BigDecimal paidAmount, LocalDate dueDate, String remark) {
        Payable value = new Payable();
        value.id = UUID.randomUUID();
        value.payableNo = payableNo;
        value.supplierId = supplierId;
        value.amount = money(amount);
        value.paidAmount = money(paidAmount);
        value.invoiceMatchedAmount = money(BigDecimal.ZERO);
        value.purchaseQuantity = qty(BigDecimal.ZERO);
        if (value.amount.signum() <= 0) throw new IllegalArgumentException("期初应付金额必须大于 0");
        if (value.paidAmount.signum() < 0 || value.paidAmount.compareTo(value.amount) > 0) {
            throw new IllegalArgumentException("已付金额必须在 0 与应付金额之间");
        }
        value.dueDate = dueDate;
        value.status = value.paidAmount.compareTo(value.amount) == 0 ? PayableStatus.SETTLED
                : value.paidAmount.signum() == 0 ? PayableStatus.OPEN : PayableStatus.PARTIALLY_PAID;
        value.remark = remark;
        value.createdAt = Instant.now();
        value.updatedAt = value.createdAt;
        return value;
    }

    public void pay(BigDecimal value) {
        BigDecimal pay = money(value);
        if (pay.signum() <= 0 || pay.compareTo(outstanding()) > 0) {
            throw new IllegalArgumentException("付款金额必须大于 0 且不能超过未付余额");
        }
        this.paidAmount = money(paidAmount.add(pay));
        refreshStatus();
    }

    /** 采购退货冲减应付未付余额。 */
    public void applyReturn(BigDecimal value) {
        BigDecimal reduce = money(value);
        if (reduce.signum() <= 0) return;
        BigDecimal next = money(amount.subtract(reduce));
        if (next.compareTo(paidAmount) < 0) next = money(paidAmount);
        this.amount = next;
        refreshStatus();
    }

    public void updateRemark(String value) {
        this.remark = value == null || value.isBlank() ? null : value.trim();
    }

    public void matchInvoice(BigDecimal value) {
        BigDecimal target = money(value);
        if (target.signum() <= 0 || target.compareTo(uninvoicedAmount()) > 0) {
            throw new IllegalArgumentException("匹配金额必须大于 0 且不能超过应付未开票金额");
        }
        invoiceMatchedAmount = money(invoiceMatchedAmount.add(target));
    }

    public void unmatchInvoice(BigDecimal value) {
        BigDecimal target = money(value);
        if (target.signum() <= 0 || target.compareTo(invoiceMatchedAmount) > 0) {
            throw new IllegalArgumentException("取消匹配金额不合法");
        }
        invoiceMatchedAmount = money(invoiceMatchedAmount.subtract(target));
    }

    public BigDecimal uninvoicedAmount() {
        return money(amount.subtract(invoiceMatchedAmount).max(BigDecimal.ZERO));
    }

    public BigDecimal outstanding() {
        return money(amount.subtract(paidAmount).max(BigDecimal.ZERO));
    }

    private void refreshStatus() {
        this.status = paidAmount.compareTo(amount) >= 0 ? PayableStatus.SETTLED
                : paidAmount.signum() > 0 ? PayableStatus.PARTIALLY_PAID : PayableStatus.OPEN;
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (purchaseQuantity == null) purchaseQuantity = qty(BigDecimal.ZERO);
        if (amount == null) amount = money(BigDecimal.ZERO);
        if (paidAmount == null) paidAmount = money(BigDecimal.ZERO);
        if (invoiceMatchedAmount == null) invoiceMatchedAmount = money(BigDecimal.ZERO);
        if (status == null) status = PayableStatus.OPEN;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal qty(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getPayableNo() { return payableNo; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public UUID getPurchaseReceiptId() { return purchaseReceiptId; }
    public String getOrderNo() { return orderNo; }
    public String getReceiptNo() { return receiptNo; }
    public BigDecimal getPurchaseQuantity() { return purchaseQuantity == null ? qty(BigDecimal.ZERO) : purchaseQuantity; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getPaidAmount() { return paidAmount; }
    public BigDecimal getInvoiceMatchedAmount() {
        return invoiceMatchedAmount == null ? money(BigDecimal.ZERO) : invoiceMatchedAmount;
    }
    public BigDecimal getUninvoicedAmount() { return uninvoicedAmount(); }
    public LocalDate getDueDate() { return dueDate; }
    public PayableStatus getStatus() { return status; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}

