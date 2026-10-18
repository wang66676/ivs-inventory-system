package com.ivs.sales.order;

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

/** 出库单：出库即写库存台账、结转成本、生成应收。 */
@Entity
@Table(name = "sales_delivery", schema = "ivs")
public class SalesDelivery {

    @Id
    private UUID id;

    @Column(name = "delivery_no", nullable = false, unique = true, length = 40)
    private String deliveryNo;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "cost_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal costAmount;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SalesDelivery() {
    }

    public static SalesDelivery create(String deliveryNo, UUID orderId, UUID customerId, UUID warehouseId,
                                       BigDecimal amount, BigDecimal costAmount, String remark) {
        SalesDelivery delivery = new SalesDelivery();
        delivery.id = UUID.randomUUID();
        delivery.deliveryNo = deliveryNo;
        delivery.orderId = orderId;
        delivery.customerId = customerId;
        delivery.warehouseId = warehouseId;
        delivery.status = DeliveryStatus.POSTED;
        delivery.deliveredAt = Instant.now();
        delivery.amount = money(amount);
        delivery.costAmount = money(costAmount);
        delivery.remark = remark;
        delivery.createdAt = delivery.deliveredAt;
        delivery.updatedAt = delivery.createdAt;
        return delivery;
    }

    /** 毛利 = 出库金额 − 出库成本（成本来自库存台账结转）。 */
    public BigDecimal grossProfit() {
        return money(amount.subtract(costAmount));
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = DeliveryStatus.POSTED;
        }
        if (deliveredAt == null) {
            deliveredAt = now;
        }
        if (amount == null) amount = money(BigDecimal.ZERO);
        if (costAmount == null) costAmount = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
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
    public String getDeliveryNo() { return deliveryNo; }
    public UUID getOrderId() { return orderId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getWarehouseId() { return warehouseId; }
    public DeliveryStatus getStatus() { return status; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getCostAmount() { return costAmount; }
    public String getRemark() { return remark; }
    public long getVersion() { return version; }
}