package com.ivs.lease;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 租赁押金流水：收取、抵扣租金和退还均只追加记录。 */
@Entity
@Table(name = "lease_deposit_txn", schema = "ivs")
public class LeaseDepositTransaction {
    @Id
    private UUID id;
    @Column(name = "contract_id", nullable = false)
    private UUID contractId;
    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 20)
    private LeaseDepositTxnType txnType;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;
    @Column(length = 30)
    private String method;
    @Column(name = "reference_no", length = 60)
    private String referenceNo;
    @Column(length = 300)
    private String remark;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LeaseDepositTransaction() {
    }

    public static LeaseDepositTransaction create(UUID contractId, LeaseDepositTxnType type, BigDecimal amount,
                                                 LocalDate txnDate, String method, String referenceNo, String remark) {
        LeaseDepositTransaction transaction = new LeaseDepositTransaction();
        transaction.id = UUID.randomUUID();
        transaction.contractId = contractId;
        transaction.txnType = type;
        transaction.amount = (amount == null ? BigDecimal.ZERO : amount).setScale(2, RoundingMode.HALF_UP);
        if (transaction.amount.signum() <= 0) throw new IllegalArgumentException("押金流水金额必须大于 0");
        transaction.txnDate = txnDate == null ? LocalDate.now() : txnDate;
        transaction.method = trimToNull(method);
        transaction.referenceNo = trimToNull(referenceNo);
        transaction.remark = trimToNull(remark);
        transaction.createdAt = Instant.now();
        return transaction;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public UUID getContractId() { return contractId; }
    public LeaseDepositTxnType getTxnType() { return txnType; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getTxnDate() { return txnDate; }
    public String getMethod() { return method; }
    public String getReferenceNo() { return referenceNo; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
}
