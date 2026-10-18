package com.ivs.policy.fund;

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

/** 按厂家分账的子余额：A 厂返利只能抵 A 厂车款。 */
@Entity
@Table(name = "rebate_fund_supplier_balance", schema = "ivs")
public class RebateFundSupplierBalance {

    @Id
    private UUID id;

    @Column(name = "pool_id", nullable = false)
    private UUID poolId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "balance_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAmount;

    @Column(name = "total_in_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalInAmount;

    @Column(name = "total_offset_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalOffsetAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected RebateFundSupplierBalance() {
    }

    public static RebateFundSupplierBalance empty(UUID poolId, UUID supplierId) {
        RebateFundSupplierBalance balance = new RebateFundSupplierBalance();
        balance.id = UUID.randomUUID();
        balance.poolId = poolId;
        balance.supplierId = supplierId;
        balance.balanceAmount = money(BigDecimal.ZERO);
        balance.totalInAmount = money(BigDecimal.ZERO);
        balance.totalOffsetAmount = money(BigDecimal.ZERO);
        balance.createdAt = Instant.now();
        balance.updatedAt = balance.createdAt;
        return balance;
    }

    public void credit(BigDecimal amount) {
        BigDecimal value = money(amount);
        this.balanceAmount = money(balanceAmount.add(value));
        this.totalInAmount = money(totalInAmount.add(value));
    }

    public void reverseCredit(BigDecimal amount) {
        BigDecimal value = money(amount);
        if (value.signum() <= 0 || value.compareTo(balanceAmount) > 0) {
            throw new IllegalStateException("该厂家返利余额不足，不能回滚期初");
        }
        this.balanceAmount = money(balanceAmount.subtract(value));
        this.totalInAmount = money(totalInAmount.subtract(value));
    }

    public void debit(BigDecimal amount) {
        BigDecimal value = money(amount);
        if (value.compareTo(balanceAmount) > 0) {
            throw new IllegalStateException("该厂家返利余额不足，当前余额：" + balanceAmount.toPlainString());
        }
        this.balanceAmount = money(balanceAmount.subtract(value));
        this.totalOffsetAmount = money(totalOffsetAmount.add(value));
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (balanceAmount == null) balanceAmount = money(BigDecimal.ZERO);
        if (totalInAmount == null) totalInAmount = money(BigDecimal.ZERO);
        if (totalOffsetAmount == null) totalOffsetAmount = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getPoolId() { return poolId; }
    public UUID getSupplierId() { return supplierId; }
    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public BigDecimal getTotalInAmount() { return totalInAmount; }
    public BigDecimal getTotalOffsetAmount() { return totalOffsetAmount; }
    public long getVersion() { return version; }
}
