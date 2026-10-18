package com.ivs.sales.settlement;

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
import java.time.LocalDate;
import java.util.UUID;

/** 应收：按出库单生成，支持部分收款与到期日管理。 */
@Entity
@Table(name = "receivable", schema = "ivs")
public class Receivable {

    @Id
    private UUID id;

    @Column(name = "receivable_no", nullable = false, unique = true, length = 40)
    private String receivableNo;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "received_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal receivedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReceivableStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Receivable() {
    }

    public static Receivable create(String receivableNo, UUID customerId, UUID orderId, UUID deliveryId,
                                    BigDecimal amount, LocalDate dueDate, String remark) {
        Receivable receivable = new Receivable();
        receivable.id = UUID.randomUUID();
        receivable.receivableNo = receivableNo;
        receivable.customerId = customerId;
        receivable.orderId = orderId;
        receivable.deliveryId = deliveryId;
        receivable.amount = money(amount);
        if (receivable.amount.signum() <= 0) {
            throw new IllegalArgumentException("应收金额必须大于 0");
        }
        receivable.receivedAmount = money(BigDecimal.ZERO);
        receivable.status = ReceivableStatus.OPEN;
        receivable.dueDate = dueDate;
        receivable.remark = remark;
        receivable.createdAt = Instant.now();
        receivable.updatedAt = receivable.createdAt;
        return receivable;
    }

    /** 历史数据迁移：按来源应收余额导入期初应收。 */
    public static Receivable importOpening(String receivableNo, UUID customerId, BigDecimal amount,
                                           BigDecimal receivedAmount, LocalDate dueDate, String remark) {
        Receivable receivable = new Receivable();
        receivable.id = UUID.randomUUID();
        receivable.receivableNo = receivableNo;
        receivable.customerId = customerId;
        receivable.amount = money(amount);
        receivable.receivedAmount = money(receivedAmount);
        if (receivable.amount.signum() <= 0) throw new IllegalArgumentException("期初应收金额必须大于 0");
        if (receivable.receivedAmount.signum() < 0 || receivable.receivedAmount.compareTo(receivable.amount) > 0) {
            throw new IllegalArgumentException("已收金额必须在 0 与应收金额之间");
        }
        receivable.status = receivable.receivedAmount.compareTo(receivable.amount) == 0
                ? ReceivableStatus.SETTLED
                : receivable.receivedAmount.signum() == 0 ? ReceivableStatus.OPEN : ReceivableStatus.PARTIALLY_SETTLED;
        receivable.dueDate = dueDate;
        receivable.remark = remark;
        receivable.createdAt = Instant.now();
        receivable.updatedAt = receivable.createdAt;
        return receivable;
    }
    /** 核销收款：超额直接拒绝，防止把应收核销成负数。 */
    public void allocate(BigDecimal amount) {
        BigDecimal value = money(amount);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("核销金额必须大于 0");
        }
        if (value.compareTo(outstanding()) > 0) {
            throw new IllegalStateException("核销金额超过未收余额，未收: " + outstanding().toPlainString());
        }
        this.receivedAmount = money(this.receivedAmount.add(value));
        this.status = this.receivedAmount.compareTo(this.amount) == 0
                ? ReceivableStatus.SETTLED : ReceivableStatus.PARTIALLY_SETTLED;
    }

    /** 销售退货冲减应收；若客户已多付，返回应退金额。 */
    public BigDecimal applyReturn(BigDecimal returnAmount) {
        BigDecimal value = money(returnAmount);
        if (value.signum() <= 0) return money(BigDecimal.ZERO);
        BigDecimal nextAmount = money(amount.subtract(value));
        if (nextAmount.signum() < 0) {
            throw new IllegalStateException("退货金额超过原应收金额");
        }
        BigDecimal refund = BigDecimal.ZERO.setScale(2);
        if (nextAmount.compareTo(receivedAmount) < 0) {
            refund = money(receivedAmount.subtract(nextAmount));
            this.amount = money(receivedAmount);
            this.status = ReceivableStatus.SETTLED;
        } else {
            this.amount = nextAmount;
            if (receivedAmount.signum() == 0) {
                this.status = ReceivableStatus.OPEN;
            } else if (receivedAmount.compareTo(amount) == 0) {
                this.status = ReceivableStatus.SETTLED;
            } else {
                this.status = ReceivableStatus.PARTIALLY_SETTLED;
            }
        }
        return refund;
    }
    public BigDecimal outstanding() {
        return money(amount.subtract(receivedAmount));
    }

    public boolean settled() {
        return status == ReceivableStatus.SETTLED;
    }

    public void markOverdue(LocalDate today) {
        if (status == ReceivableStatus.SETTLED || dueDate == null) {
            return;
        }
        if (dueDate.isBefore(today)) {
            this.status = ReceivableStatus.OVERDUE;
        }
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (receivedAmount == null) {
            receivedAmount = money(BigDecimal.ZERO);
        }
        if (status == null) {
            status = ReceivableStatus.OPEN;
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

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getReceivableNo() { return receivableNo; }
    public UUID getCustomerId() { return customerId; }
    public UUID getOrderId() { return orderId; }
    public UUID getDeliveryId() { return deliveryId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getReceivedAmount() { return receivedAmount; }
    public ReceivableStatus getStatus() { return status; }
    public LocalDate getDueDate() { return dueDate; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}

