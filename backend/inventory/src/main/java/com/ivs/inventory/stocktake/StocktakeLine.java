package com.ivs.inventory.stocktake;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 盘点明细：账面数为盘点时快照，差异 = 实盘 - 账面。 */
@Entity
@Table(name = "stocktake_line", schema = "ivs")
public class StocktakeLine {

    @Id private UUID id;
    @Column(name = "stocktake_id", nullable = false) private UUID stocktakeId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "batch_no", nullable = false, length = 60) private String batchNo;
    @Column(name = "book_qty", nullable = false, precision = 18, scale = 3) private BigDecimal bookQty;
    @Column(name = "actual_qty", nullable = false, precision = 18, scale = 3) private BigDecimal actualQty;
    @Column(name = "difference_qty", nullable = false, precision = 18, scale = 3) private BigDecimal differenceQty;
    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2) private BigDecimal unitCost;
    @Column(name = "difference_reason", length = 300) private String differenceReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected StocktakeLine() {
    }

    public static StocktakeLine create(UUID stocktakeId, UUID productId, String batchNo,
                                       BigDecimal bookQty, BigDecimal actualQty, BigDecimal unitCost,
                                       String differenceReason) {
        StocktakeLine line = new StocktakeLine();
        line.id = UUID.randomUUID();
        line.stocktakeId = stocktakeId;
        line.productId = productId;
        line.batchNo = normalizeBatch(batchNo);
        line.bookQty = qty(bookQty);
        line.unitCost = money(unitCost);
        line.update(actualQty, differenceReason, unitCost);
        line.createdAt = Instant.now();
        line.updatedAt = line.createdAt;
        return line;
    }

    public void update(BigDecimal actual, String reason, BigDecimal cost) {
        this.actualQty = qty(actual);
        if (this.actualQty.signum() < 0) throw new IllegalArgumentException("实盘数量不能为负");
        this.differenceQty = qty(this.actualQty.subtract(bookQty));
        this.unitCost = money(cost);
        this.differenceReason = reason == null || reason.isBlank() ? null : reason.trim();
        this.updatedAt = Instant.now();
    }

    public void validateReason() {
        if (differenceQty.signum() != 0 && differenceReason == null) {
            throw new IllegalStateException("存在盘点差异时必须填写差异原因");
        }
    }

    private static String normalizeBatch(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private static BigDecimal qty(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (bookQty == null) bookQty = qty(BigDecimal.ZERO);
        if (actualQty == null) actualQty = qty(BigDecimal.ZERO);
        if (differenceQty == null) differenceQty = qty(BigDecimal.ZERO);
        if (unitCost == null) unitCost = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getStocktakeId() { return stocktakeId; }
    public UUID getProductId() { return productId; }
    public String getBatchNo() { return batchNo; }
    public BigDecimal getBookQty() { return bookQty; }
    public BigDecimal getActualQty() { return actualQty; }
    public BigDecimal getDifferenceQty() { return differenceQty; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getDifferenceReason() { return differenceReason; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
