package com.ivs.policy.fund;

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
 * 资金池流水（只追加）：入池记录带 remaining_amount，抵扣时按 FIFO 消费，
 * 从而实现「哪几笔返利抵了哪一张采购单」的双向追溯。
 */
@Entity
@Table(name = "rebate_fund_txn", schema = "ivs")
public class RebateFundTxn {

    @Id
    private UUID id;

    @Column(name = "pool_id", nullable = false)
    private UUID poolId;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FundDirection direction;

    @Column(name = "txn_type", nullable = false, length = 30)
    private String txnType;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    /** 仅入池记录使用：尚未被抵扣的金额。 */
    @Column(name = "remaining_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal remainingAmount;

    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "source_no", length = 60)
    private String sourceNo;

    @Column(name = "offset_no", length = 40)
    private String offsetNo;

    @Column(name = "purchase_order_id")
    private UUID purchaseOrderId;

    @Column(name = "purchase_receipt_id")
    private UUID purchaseReceiptId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "operator_id")
    private UUID operatorId;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RebateFundTxn() {
    }

    public static RebateFundTxn credit(UUID poolId, UUID supplierId, BigDecimal amount, BigDecimal balanceAfter,
                                       String txnType, String sourceType, UUID sourceId, String sourceNo,
                                       UUID operatorId, String remark) {
        RebateFundTxn txn = new RebateFundTxn();
        txn.id = UUID.randomUUID();
        txn.poolId = poolId;
        txn.supplierId = supplierId;
        txn.direction = FundDirection.IN;
        txn.txnType = txnType;
        txn.amount = money(amount);
        txn.balanceAfter = money(balanceAfter);
        txn.remainingAmount = money(amount);
        txn.sourceType = sourceType;
        txn.sourceId = sourceId;
        txn.sourceNo = sourceNo;
        txn.operatorId = operatorId;
        txn.remark = remark;
        txn.occurredAt = Instant.now();
        txn.createdAt = txn.occurredAt;
        return txn;
    }

    public static RebateFundTxn offset(UUID poolId, UUID supplierId, BigDecimal amount, BigDecimal balanceAfter,
                                       String offsetNo, UUID purchaseOrderId, UUID purchaseReceiptId,
                                       UUID operatorId, String remark) {
        RebateFundTxn txn = new RebateFundTxn();
        txn.id = UUID.randomUUID();
        txn.poolId = poolId;
        txn.supplierId = supplierId;
        txn.direction = FundDirection.OUT;
        txn.txnType = "PURCHASE_OFFSET";
        txn.amount = money(amount);
        txn.balanceAfter = money(balanceAfter);
        txn.remainingAmount = money(BigDecimal.ZERO);
        txn.offsetNo = offsetNo;
        txn.purchaseOrderId = purchaseOrderId;
        txn.purchaseReceiptId = purchaseReceiptId;
        txn.operatorId = operatorId;
        txn.remark = remark;
        txn.occurredAt = Instant.now();
        txn.createdAt = txn.occurredAt;
        return txn;
    }

    public static RebateFundTxn adjustIn(UUID poolId, UUID supplierId, BigDecimal amount, BigDecimal balanceAfter,
                                         String offsetNo, UUID operatorId, String remark) {
        RebateFundTxn txn = new RebateFundTxn();
        txn.id = UUID.randomUUID();
        txn.poolId = poolId;
        txn.supplierId = supplierId;
        txn.direction = FundDirection.IN;
        txn.txnType = "ADJUST_IN";
        txn.amount = money(amount);
        txn.balanceAfter = money(balanceAfter);
        txn.remainingAmount = money(BigDecimal.ZERO);
        txn.offsetNo = offsetNo;
        txn.operatorId = operatorId;
        txn.remark = remark;
        txn.occurredAt = Instant.now();
        txn.createdAt = txn.occurredAt;
        return txn;
    }

    /** 抵扣时消费本笔入池记录的一部分。 */
    public void consume(BigDecimal value) {
        BigDecimal amountToConsume = money(value);
        if (amountToConsume.compareTo(remainingAmount) > 0) {
            throw new IllegalStateException("入池记录可用余额不足");
        }
        this.remainingAmount = money(remainingAmount.subtract(amountToConsume));
    }

    /** 取消抵扣时回补。 */
    public void restore(BigDecimal value) {
        this.remainingAmount = money(remainingAmount.add(value));
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (amount == null) amount = money(BigDecimal.ZERO);
        if (balanceAfter == null) balanceAfter = money(BigDecimal.ZERO);
        if (remainingAmount == null) remainingAmount = money(BigDecimal.ZERO);
        if (occurredAt == null) occurredAt = now;
        if (createdAt == null) createdAt = now;
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getPoolId() { return poolId; }
    public UUID getSupplierId() { return supplierId; }
    public FundDirection getDirection() { return direction; }
    public String getTxnType() { return txnType; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public String getSourceType() { return sourceType; }
    public UUID getSourceId() { return sourceId; }
    public String getSourceNo() { return sourceNo; }
    public String getOffsetNo() { return offsetNo; }
    public UUID getPurchaseOrderId() { return purchaseOrderId; }
    public UUID getPurchaseReceiptId() { return purchaseReceiptId; }
    public Instant getOccurredAt() { return occurredAt; }
    public String getRemark() { return remark; }
}