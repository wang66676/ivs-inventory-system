package com.ivs.inventory.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * 库存台账：只追加的事实表，任何库存变动都写这里。
 *
 * <p>不存在 update/delete 语义，因此没有 updated_at 与乐观锁版本；
 * 库存余额（{@link StockBalance}）是可由此表重建的投影。</p>
 */
@Entity
@Table(name = "stock_ledger", schema = "ivs")
public class StockLedger {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** 序列号商品必填，数量商品为空。 */
    @Column(name = "serial_id")
    private UUID serialId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "move_type", nullable = false, length = 30)
    private StockMoveType moveType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StockDirection direction;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 3)
    private BigDecimal balanceAfter;

    @Column(name = "biz_type", length = 40)
    private String bizType;

    @Column(name = "biz_id")
    private UUID bizId;

    @Column(name = "biz_no", length = 60)
    private String bizNo;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "operator_id")
    private UUID operatorId;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockLedger() {
    }

    public static StockLedger record(UUID productId, UUID serialId, UUID warehouseId, StockMoveType moveType,
                                     StockDirection direction, BigDecimal quantity, BigDecimal unitCost,
                                     BigDecimal balanceAfter, String bizType, UUID bizId, String bizNo,
                                     UUID operatorId, String remark) {
        StockLedger ledger = new StockLedger();
        ledger.id = UUID.randomUUID();
        ledger.productId = productId;
        ledger.serialId = serialId;
        ledger.warehouseId = warehouseId;
        ledger.moveType = moveType;
        ledger.direction = direction;
        ledger.quantity = qty(quantity);
        ledger.unitCost = money(unitCost);
        ledger.amount = money(ledger.quantity.multiply(ledger.unitCost));
        ledger.balanceAfter = qty(balanceAfter);
        ledger.bizType = bizType;
        ledger.bizId = bizId;
        ledger.bizNo = bizNo;
        ledger.operatorId = operatorId;
        ledger.remark = remark;
        ledger.occurredAt = Instant.now();
        ledger.createdAt = ledger.occurredAt;
        return ledger;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (occurredAt == null) {
            occurredAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
    }

    private static BigDecimal qty(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public UUID getSerialId() { return serialId; }
    public UUID getWarehouseId() { return warehouseId; }
    public StockMoveType getMoveType() { return moveType; }
    public StockDirection getDirection() { return direction; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public String getBizType() { return bizType; }
    public UUID getBizId() { return bizId; }
    public String getBizNo() { return bizNo; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getOperatorId() { return operatorId; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
}