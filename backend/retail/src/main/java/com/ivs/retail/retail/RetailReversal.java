package com.ivs.retail.retail;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** 实销冲正记录：退车 / 退牌 / 换车，保留原因与新序列号（换车时）。 */
@Entity
@Table(name = "retail_reversal", schema = "ivs")
public class RetailReversal {

    @Id
    private UUID id;

    @Column(name = "retail_id", nullable = false)
    private UUID retailId;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(name = "new_serial_id")
    private UUID newSerialId;

    @Column(name = "reversed_by")
    private UUID reversedBy;

    @Column(name = "reversed_at", nullable = false)
    private Instant reversedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RetailReversal() {
    }

    public static RetailReversal create(UUID retailId, String reason, UUID newSerialId, UUID reversedBy) {
        RetailReversal reversal = new RetailReversal();
        reversal.id = UUID.randomUUID();
        reversal.retailId = retailId;
        reversal.reason = reason;
        reversal.newSerialId = newSerialId;
        reversal.reversedBy = reversedBy;
        reversal.reversedAt = Instant.now();
        reversal.createdAt = reversal.reversedAt;
        return reversal;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (reversedAt == null) reversedAt = Instant.now();
        if (createdAt == null) createdAt = reversedAt;
    }

    public UUID getId() { return id; }
    public UUID getRetailId() { return retailId; }
    public String getReason() { return reason; }
    public UUID getNewSerialId() { return newSerialId; }
    public UUID getReversedBy() { return reversedBy; }
    public Instant getReversedAt() { return reversedAt; }
}