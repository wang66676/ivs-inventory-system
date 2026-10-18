package com.ivs.policy.fund;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 抵扣明细：哪一笔返利入池记录、抵了多少。 */
@Entity
@Table(name = "rebate_offset_line", schema = "ivs")
public class RebateOffsetLine {

    @Id
    private UUID id;

    @Column(name = "offset_id", nullable = false)
    private UUID offsetId;

    @Column(name = "fund_txn_id", nullable = false)
    private UUID fundTxnId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RebateOffsetLine() {
    }

    public static RebateOffsetLine create(UUID offsetId, UUID fundTxnId, BigDecimal amount) {
        RebateOffsetLine line = new RebateOffsetLine();
        line.id = UUID.randomUUID();
        line.offsetId = offsetId;
        line.fundTxnId = fundTxnId;
        line.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        line.createdAt = Instant.now();
        return line;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getOffsetId() { return offsetId; }
    public UUID getFundTxnId() { return fundTxnId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}