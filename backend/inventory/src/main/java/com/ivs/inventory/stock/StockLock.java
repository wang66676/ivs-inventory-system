package com.ivs.inventory.stock;

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
import java.util.UUID;

/** 库存锁定（预留）：销售订单锁货使用，带超时自动释放。 */
@Entity
@Table(name = "stock_lock", schema = "ivs")
public class StockLock {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "serial_id")
    private UUID serialId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "batch_no", length = 60)
    private String batchNo;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockLockStatus status;

    @Column(name = "biz_type", length = 40)
    private String bizType;

    @Column(name = "biz_id")
    private UUID bizId;

    @Column(name = "biz_no", length = 60)
    private String bizNo;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected StockLock() {
    }

    public static StockLock create(UUID productId, UUID serialId, UUID warehouseId, String batchNo,
                                   BigDecimal quantity, String bizType, UUID bizId, String bizNo,
                                   Instant expiresAt) {
        StockLock lock = new StockLock();
        lock.id = UUID.randomUUID();
        lock.productId = productId;
        lock.serialId = serialId;
        lock.warehouseId = warehouseId;
        lock.batchNo = batchNo;
        lock.quantity = (quantity == null ? BigDecimal.ZERO : quantity).setScale(3, RoundingMode.HALF_UP);
        lock.status = StockLockStatus.ACTIVE;
        lock.bizType = bizType;
        lock.bizId = bizId;
        lock.bizNo = bizNo;
        lock.expiresAt = expiresAt;
        lock.createdAt = Instant.now();
        lock.updatedAt = lock.createdAt;
        return lock;
    }

    public void release() {
        if (status == StockLockStatus.ACTIVE) {
            this.status = StockLockStatus.RELEASED;
        }
    }

    public void expire() {
        if (status == StockLockStatus.ACTIVE) {
            this.status = StockLockStatus.EXPIRED;
        }
    }

    public void consume() {
        if (status == StockLockStatus.ACTIVE) {
            this.status = StockLockStatus.CONSUMED;
        }
    }

    public boolean isActive() {
        return status == StockLockStatus.ACTIVE;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = StockLockStatus.ACTIVE;
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
    public UUID getProductId() { return productId; }
    public UUID getSerialId() { return serialId; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getBatchNo() { return batchNo; }
    public BigDecimal getQuantity() { return quantity; }
    public StockLockStatus getStatus() { return status; }
    public String getBizType() { return bizType; }
    public UUID getBizId() { return bizId; }
    public String getBizNo() { return bizNo; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}