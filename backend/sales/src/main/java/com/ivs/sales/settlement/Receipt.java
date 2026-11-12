package com.ivs.sales.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

/** 收款单：登记收款并按到期日 FIFO 核销应收。 */
@Entity
@Table(name = "receipt", schema = "ivs")
public class Receipt {

    @Id
    private UUID id;

    @Column(name = "receipt_no", nullable = false, unique = true, length = 40)
    private String receiptNo;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentMethod method;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceiptStatus status;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Receipt() {
    }

    public static Receipt create(String receiptNo, UUID customerId, BigDecimal amount, PaymentMethod method,
                                 String remark, LocalDate receivedDate) {
        Receipt receipt = new Receipt();
        receipt.id = UUID.randomUUID();
        receipt.receiptNo = receiptNo;
        receipt.customerId = customerId;
        receipt.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        if (receipt.amount.signum() <= 0) {
            throw new IllegalArgumentException("收款金额必须大于 0");
        }
        receipt.method = method == null ? PaymentMethod.BANK_TRANSFER : method;
        receipt.receivedAt = receivedDate == null ? Instant.now()
                : receivedDate.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant();
        receipt.status = ReceiptStatus.POSTED;
        receipt.remark = remark;
        receipt.createdAt = receipt.receivedAt;
        receipt.updatedAt = receipt.createdAt;
        return receipt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (method == null) {
            method = PaymentMethod.BANK_TRANSFER;
        }
        if (receivedAt == null) {
            receivedAt = now;
        }
        if (status == null) {
            status = ReceiptStatus.POSTED;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getReceiptNo() { return receiptNo; }
    public UUID getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public PaymentMethod getMethod() { return method; }
    public Instant getReceivedAt() { return receivedAt; }
    public ReceiptStatus getStatus() { return status; }
    public String getRemark() { return remark; }
    public long getVersion() { return version; }
}