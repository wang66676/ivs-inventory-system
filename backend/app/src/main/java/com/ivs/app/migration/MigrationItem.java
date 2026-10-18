package com.ivs.app.migration;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 迁移明细：记录每一条导入数据的目标 ID，支持对账与回滚。 */
@Entity
@Table(name = "data_migration_item", schema = "ivs")
public class MigrationItem {

    @Id private UUID id;
    @Column(name = "batch_id", nullable = false) private UUID batchId;
    @Column(name = "item_type", nullable = false, length = 40) private String itemType;
    @Column(name = "source_key", nullable = false, length = 200) private String sourceKey;
    @Column(name = "target_id") private UUID targetId;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected MigrationItem() {
    }

    public static MigrationItem create(UUID batchId, String itemType, String sourceKey,
                                       UUID targetId, BigDecimal amount) {
        MigrationItem item = new MigrationItem();
        item.id = UUID.randomUUID();
        item.batchId = batchId;
        item.itemType = itemType;
        item.sourceKey = sourceKey;
        item.targetId = targetId;
        item.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        item.createdAt = Instant.now();
        return item;
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (amount == null) amount = BigDecimal.ZERO.setScale(2);
        if (createdAt == null) createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getBatchId() { return batchId; }
    public String getItemType() { return itemType; }
    public String getSourceKey() { return sourceKey; }
    public UUID getTargetId() { return targetId; }
    public BigDecimal getAmount() { return amount; }
    public Instant getCreatedAt() { return createdAt; }
}
