package com.ivs.inventory.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * 库存余额：商品 × 仓库 × 批次的在库 / 锁定 / 可用数量与成本。
 *
 * <p>由库存台账投影得到，可重建；数量商品按移动加权平均维护单位成本，
 * 序列号商品因每个序列号单独计价，批次维度汇总单位成本仅供参考。</p>
 */
@Entity
@Table(name = "stock_balance", schema = "ivs")
public class StockBalance {

    public static final String NO_BATCH = "-";

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "batch_no", nullable = false, length = 60)
    private String batchNo;

    @Column(name = "on_hand_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal onHandQty;

    @Column(name = "locked_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal lockedQty;

    @Column(name = "available_qty", nullable = false, precision = 18, scale = 3)
    private BigDecimal availableQty;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected StockBalance() {
    }

    public static StockBalance empty(UUID productId, UUID warehouseId, String batchNo) {
        StockBalance balance = new StockBalance();
        balance.id = UUID.randomUUID();
        balance.productId = productId;
        balance.warehouseId = warehouseId;
        balance.batchNo = batchNo == null || batchNo.isBlank() ? NO_BATCH : batchNo.trim();
        balance.onHandQty = qty(BigDecimal.ZERO);
        balance.lockedQty = qty(BigDecimal.ZERO);
        balance.availableQty = qty(BigDecimal.ZERO);
        balance.unitCost = money(BigDecimal.ZERO);
        balance.amount = money(BigDecimal.ZERO);
        balance.createdAt = Instant.now();
        balance.updatedAt = balance.createdAt;
        return balance;
    }

    /**
     * 入库：数量商品按移动加权平均重算单位成本；序列号商品按单个序列号成本累积。
     *
     * @param weighted true = 移动加权平均（数量商品），false = 个别计价（序列号商品按台）
     */
    public void receive(BigDecimal quantity, BigDecimal inboundUnitCost, boolean weighted) {
        BigDecimal inQty = qty(quantity);
        BigDecimal inCost = money(inboundUnitCost);
        if (inQty.signum() <= 0) {
            throw new IllegalArgumentException("入库数量必须大于 0");
        }
        BigDecimal nextQty = qty(onHandQty.add(inQty));
        BigDecimal nextAmount = money(amount.add(inQty.multiply(inCost)));
        this.unitCost = weighted
                ? (nextQty.signum() == 0 ? inCost : money(nextAmount.divide(nextQty, 4, RoundingMode.HALF_UP)))
                : inCost;
        this.onHandQty = nextQty;
        this.amount = nextAmount;
        recalcAvailable();
    }

    /** 出库：数量商品按当前移动加权单位成本结转；序列号商品按该序列号已计入的成本结转。 */
    public void issue(BigDecimal quantity, BigDecimal outboundUnitCost) {
        BigDecimal outQty = qty(quantity);
        if (outQty.signum() <= 0) {
            throw new IllegalArgumentException("出库数量必须大于 0");
        }
        if (outQty.compareTo(onHandQty) > 0) {
            throw new IllegalStateException("库存不足，当前在库: " + onHandQty.toPlainString());
        }
        BigDecimal cost = outboundUnitCost == null ? unitCost : money(outboundUnitCost);
        this.onHandQty = qty(onHandQty.subtract(outQty));
        this.amount = money(amount.subtract(outQty.multiply(cost)));
        if (this.amount.signum() < 0) {
            this.amount = money(BigDecimal.ZERO);
        }
        if (this.onHandQty.signum() == 0) {
            this.onHandQty = qty(BigDecimal.ZERO);
            this.amount = money(BigDecimal.ZERO);
        }
        recalcAvailable();
    }

    /** 调整（盘点差异 / 手工调整）：直接落到目标数量，金额按目标数量与当前单位成本重算。 */
    public void adjustTo(BigDecimal targetQuantity, BigDecimal targetUnitCost) {
        BigDecimal target = qty(targetQuantity);
        if (target.signum() < 0) {
            throw new IllegalArgumentException("调整后库存不能为负数");
        }
        this.onHandQty = target;
        if (targetUnitCost != null) {
            this.unitCost = money(targetUnitCost);
        }
        this.amount = money(target.multiply(this.unitCost));
        recalcAvailable();
    }

    public void lock(BigDecimal quantity) {
        BigDecimal qty = qty(quantity);
        if (qty.signum() <= 0) {
            throw new IllegalArgumentException("锁定数量必须大于 0");
        }
        if (qty.compareTo(availableQty) > 0) {
            throw new IllegalStateException("可用库存不足，可用: " + availableQty.toPlainString());
        }
        this.lockedQty = qty(lockedQty.add(qty));
        recalcAvailable();
    }

    public void unlock(BigDecimal quantity) {
        BigDecimal qty = qty(quantity);
        BigDecimal next = lockedQty.subtract(qty);
        this.lockedQty = next.signum() < 0 ? qty(BigDecimal.ZERO) : qty(next);
        recalcAvailable();
    }

    private void recalcAvailable() {
        BigDecimal available = onHandQty.subtract(lockedQty);
        this.availableQty = available.signum() < 0 ? qty(BigDecimal.ZERO) : qty(available);
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (batchNo == null) {
            batchNo = NO_BATCH;
        }
        if (onHandQty == null) onHandQty = qty(BigDecimal.ZERO);
        if (lockedQty == null) lockedQty = qty(BigDecimal.ZERO);
        if (availableQty == null) recalcAvailable();
        if (unitCost == null) unitCost = money(BigDecimal.ZERO);
        if (amount == null) amount = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }

    private static BigDecimal qty(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getBatchNo() { return batchNo; }
    public BigDecimal getOnHandQty() { return onHandQty; }
    public BigDecimal getLockedQty() { return lockedQty; }
    public BigDecimal getAvailableQty() { return availableQty; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getAmount() { return amount; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}