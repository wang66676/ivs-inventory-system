package com.ivs.inventory.stocktake;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 库存盘点单头：账面数量在创建/新增明细时快照，过账后不可修改。 */
@Entity
@Table(name = "stocktake", schema = "ivs")
public class Stocktake {

    @Id private UUID id;
    @Column(name = "stocktake_no", nullable = false, unique = true, length = 40) private String stocktakeNo;
    @Column(name = "warehouse_id", nullable = false) private UUID warehouseId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StocktakeStatus status;
    @Column(name = "line_count", nullable = false) private int lineCount;
    @Column(name = "difference_count", nullable = false) private int differenceCount;
    @Column(name = "total_difference_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal totalDifferenceQty;
    @Column(length = 300) private String remark;
    @Column(name = "posted_by") private UUID postedBy;
    @Column(name = "posted_at") private Instant postedAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected Stocktake() {
    }

    public static Stocktake create(String stocktakeNo, UUID warehouseId, UUID createdBy, String remark) {
        Stocktake stocktake = new Stocktake();
        stocktake.id = UUID.randomUUID();
        stocktake.stocktakeNo = stocktakeNo;
        stocktake.warehouseId = warehouseId;
        stocktake.status = StocktakeStatus.DRAFT;
        stocktake.lineCount = 0;
        stocktake.differenceCount = 0;
        stocktake.totalDifferenceQty = qty(BigDecimal.ZERO);
        stocktake.remark = remark;
        stocktake.createdBy = createdBy;
        stocktake.createdAt = Instant.now();
        stocktake.updatedAt = stocktake.createdAt;
        return stocktake;
    }

    public void summarize(List<StocktakeLine> lines) {
        if (status != StocktakeStatus.DRAFT) throw new IllegalStateException("只有草稿盘点单可以修改");
        this.lineCount = lines.size();
        this.differenceCount = (int) lines.stream().filter(line -> line.getDifferenceQty().signum() != 0).count();
        this.totalDifferenceQty = qty(lines.stream().map(StocktakeLine::getDifferenceQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public void post(UUID operatorId) {
        if (status != StocktakeStatus.DRAFT && status != StocktakeStatus.PENDING_APPROVAL) {
            throw new IllegalStateException("只有草稿或待审批盘点单可以过账");
        }
        this.status = StocktakeStatus.POSTED;
        this.postedBy = operatorId;
        this.postedAt = Instant.now();
    }

    public void markPendingApproval() {
        if (status != StocktakeStatus.DRAFT) throw new IllegalStateException("只有草稿盘点单可以提交审批");
        status = StocktakeStatus.PENDING_APPROVAL;
    }

    public void rejectApproval() {
        if (status != StocktakeStatus.PENDING_APPROVAL) throw new IllegalStateException("盘点单不在待审批状态");
        status = StocktakeStatus.DRAFT;
    }

    public void cancel() {
        if (status == StocktakeStatus.POSTED) throw new IllegalStateException("已过账盘点单不能取消");
        this.status = StocktakeStatus.CANCELLED;
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = StocktakeStatus.DRAFT;
        if (totalDifferenceQty == null) totalDifferenceQty = qty(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal qty(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getStocktakeNo() { return stocktakeNo; }
    public UUID getWarehouseId() { return warehouseId; }
    public StocktakeStatus getStatus() { return status; }
    public int getLineCount() { return lineCount; }
    public int getDifferenceCount() { return differenceCount; }
    public BigDecimal getTotalDifferenceQty() { return totalDifferenceQty; }
    public String getRemark() { return remark; }
    public UUID getPostedBy() { return postedBy; }
    public Instant getPostedAt() { return postedAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
