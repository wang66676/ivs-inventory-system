package com.ivs.app.migration;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 历史数据迁移批次。 */
@Entity
@Table(name = "data_migration_batch", schema = "ivs")
public class MigrationBatch {

    @Id private UUID id;
    @Column(name = "batch_no", nullable = false, unique = true, length = 40) private String batchNo;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "source_system", length = 120) private String sourceSystem;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private MigrationStatus status;
    @Column(name = "item_count", nullable = false) private int itemCount;
    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2) private BigDecimal totalAmount;
    @Column(length = 300) private String remark;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "imported_at") private Instant importedAt;
    @Column(name = "rolled_back_at") private Instant rolledBackAt;
    @Version @Column(nullable = false) private long version;

    protected MigrationBatch() {
    }

    public static MigrationBatch imported(String batchNo, String name, String sourceSystem,
                                          String remark, UUID createdBy) {
        MigrationBatch value = new MigrationBatch();
        value.id = UUID.randomUUID();
        value.batchNo = batchNo;
        value.name = name;
        value.sourceSystem = sourceSystem;
        value.status = MigrationStatus.IMPORTED;
        value.totalAmount = BigDecimal.ZERO.setScale(2);
        value.remark = remark;
        value.createdBy = createdBy;
        value.createdAt = Instant.now();
        value.importedAt = value.createdAt;
        return value;
    }

    public void summarize(int itemCount, BigDecimal totalAmount) {
        this.itemCount = itemCount;
        this.totalAmount = (totalAmount == null ? BigDecimal.ZERO : totalAmount).setScale(2, RoundingMode.HALF_UP);
    }

    public void rollback() {
        if (status != MigrationStatus.IMPORTED) throw new IllegalStateException("只有已导入批次可以回滚");
        this.status = MigrationStatus.ROLLED_BACK;
        this.rolledBackAt = Instant.now();
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = MigrationStatus.IMPORTED;
        if (totalAmount == null) totalAmount = BigDecimal.ZERO.setScale(2);
        if (createdAt == null) createdAt = now;
    }

    public UUID getId() { return id; }
    public String getBatchNo() { return batchNo; }
    public String getName() { return name; }
    public String getSourceSystem() { return sourceSystem; }
    public MigrationStatus getStatus() { return status; }
    public int getItemCount() { return itemCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getRemark() { return remark; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getImportedAt() { return importedAt; }
    public Instant getRolledBackAt() { return rolledBackAt; }
}
