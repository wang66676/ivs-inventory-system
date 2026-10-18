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

/**
 * 返利资金池：全公司只有一个池，余额按厂家分子账（见 {@link RebateFundSupplierBalance}）。
 * 只能抵扣同一厂家的车款；不允许预抵扣（allow_pre_offset 固定 false）。
 */
@Entity
@Table(name = "rebate_fund_pool", schema = "ivs")
public class RebateFundPool {

    public static final String POOL_CODE = "REBATE-POOL-001";

    @Id
    private UUID id;

    @Column(name = "pool_code", nullable = false, unique = true, length = 40)
    private String poolCode;

    @Column(name = "pool_name", nullable = false, length = 120)
    private String poolName;

    @Column(name = "org_id")
    private UUID orgId;

    @Column(name = "balance_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAmount;

    @Column(name = "total_in_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalInAmount;

    @Column(name = "total_offset_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalOffsetAmount;

    /** 固定 false：池内只有已到账返利，不存在预抵扣。 */
    @Column(name = "allow_pre_offset", nullable = false)
    private boolean allowPreOffset;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected RebateFundPool() {
    }

    public static RebateFundPool create(String poolName, UUID orgId) {
        RebateFundPool pool = new RebateFundPool();
        pool.id = UUID.randomUUID();
        pool.poolCode = POOL_CODE;
        pool.poolName = poolName;
        pool.orgId = orgId;
        pool.balanceAmount = money(BigDecimal.ZERO);
        pool.totalInAmount = money(BigDecimal.ZERO);
        pool.totalOffsetAmount = money(BigDecimal.ZERO);
        pool.allowPreOffset = false;
        pool.status = "ACTIVE";
        pool.createdAt = Instant.now();
        pool.updatedAt = pool.createdAt;
        return pool;
    }

    public void credit(BigDecimal amount) {
        BigDecimal value = money(amount);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("入池金额必须大于 0");
        }
        this.balanceAmount = money(balanceAmount.add(value));
        this.totalInAmount = money(totalInAmount.add(value));
    }

    public void reverseCredit(BigDecimal amount) {
        BigDecimal value = money(amount);
        if (value.signum() <= 0 || value.compareTo(balanceAmount) > 0) {
            throw new IllegalStateException("资金池余额不足或金额无效，不能回滚期初");
        }
        this.balanceAmount = money(balanceAmount.subtract(value));
        this.totalInAmount = money(totalInAmount.subtract(value));
    }

    public void debit(BigDecimal amount) {
        BigDecimal value = money(amount);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("抵扣金额必须大于 0");
        }
        if (value.compareTo(balanceAmount) > 0) {
            throw new IllegalStateException("资金池余额不足");
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
        if (status == null) status = "ACTIVE";
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getPoolCode() { return poolCode; }
    public String getPoolName() { return poolName; }
    public UUID getOrgId() { return orgId; }
    public BigDecimal getBalanceAmount() { return balanceAmount; }
    public BigDecimal getTotalInAmount() { return totalInAmount; }
    public BigDecimal getTotalOffsetAmount() { return totalOffsetAmount; }
    public boolean isAllowPreOffset() { return allowPreOffset; }
    public String getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
