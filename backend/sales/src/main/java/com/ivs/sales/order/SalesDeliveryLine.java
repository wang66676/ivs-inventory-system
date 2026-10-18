package com.ivs.sales.order;

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

/** 出库明细：记录本次出库的序列号与结转成本。 */
@Entity
@Table(name = "sales_delivery_line", schema = "ivs")
public class SalesDeliveryLine {

    @Id
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Column(name = "order_line_id", nullable = false)
    private UUID orderLineId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "cost_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal costAmount;

    @Column(name = "serial_ids", columnDefinition = "text")
    private String serialIds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SalesDeliveryLine() {
    }

    public static SalesDeliveryLine create(UUID deliveryId, UUID orderLineId, UUID productId, BigDecimal quantity,
                                           BigDecimal unitPrice, BigDecimal costAmount, String serialIds) {
        SalesDeliveryLine line = new SalesDeliveryLine();
        line.id = UUID.randomUUID();
        line.deliveryId = deliveryId;
        line.orderLineId = orderLineId;
        line.productId = productId;
        line.quantity = (quantity == null ? BigDecimal.ZERO : quantity).setScale(3, RoundingMode.HALF_UP);
        line.unitPrice = (unitPrice == null ? BigDecimal.ZERO : unitPrice).setScale(2, RoundingMode.HALF_UP);
        line.costAmount = (costAmount == null ? BigDecimal.ZERO : costAmount).setScale(2, RoundingMode.HALF_UP);
        line.serialIds = serialIds;
        line.createdAt = Instant.now();
        line.updatedAt = line.createdAt;
        return line;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
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
    public UUID getDeliveryId() { return deliveryId; }
    public UUID getOrderLineId() { return orderLineId; }
    public UUID getProductId() { return productId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getCostAmount() { return costAmount; }
    public String getSerialIds() { return serialIds; }
    public long getVersion() { return version; }
}