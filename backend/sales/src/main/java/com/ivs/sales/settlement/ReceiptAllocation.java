package com.ivs.sales.settlement;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 收款核销明细：一笔收款可核销多笔应收。 */
@Entity
@Table(name = "receipt_allocation", schema = "ivs")
public class ReceiptAllocation {

    @Id
    private UUID id;

    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId;

    @Column(name = "receivable_id", nullable = false)
    private UUID receivableId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReceiptAllocation() {
    }

    public static ReceiptAllocation create(UUID receiptId, UUID receivableId, BigDecimal amount) {
        ReceiptAllocation allocation = new ReceiptAllocation();
        allocation.id = UUID.randomUUID();
        allocation.receiptId = receiptId;
        allocation.receivableId = receivableId;
        allocation.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        allocation.createdAt = Instant.now();
        return allocation;
    }

    @PrePersist
    void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public UUID getId() { return id; }
    public UUID getReceiptId() { return receiptId; }
    public UUID getReceivableId() { return receivableId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}