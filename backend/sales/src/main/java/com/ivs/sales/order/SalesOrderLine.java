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

/** 销售明细行：含成交价与最低限价快照（用于低价判断与后续追溯）。 */
@Entity
@Table(name = "sales_order_line", schema = "ivs")
public class SalesOrderLine {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "delivered_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal deliveredQuantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "min_price", precision = 18, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "tax_rate", precision = 8, scale = 4)
    private BigDecimal taxRate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    /** 序列号商品在订单上指定的序列号（JSON 数组），锁货与出库按此执行。 */
    @Column(name = "serial_ids", columnDefinition = "text")
    private String serialIds;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected SalesOrderLine() {
    }

    public static SalesOrderLine create(UUID orderId, int lineNo, UUID productId, BigDecimal quantity,
                                        BigDecimal unitPrice, BigDecimal minPrice, BigDecimal taxRate,
                                        String serialIds, String remark) {
        SalesOrderLine line = new SalesOrderLine();
        line.id = UUID.randomUUID();
        line.orderId = orderId;
        line.lineNo = lineNo;
        line.productId = productId;
        line.quantity = qty(quantity);
        if (line.quantity.signum() <= 0) {
            throw new IllegalArgumentException("销售数量必须大于 0");
        }
        line.deliveredQuantity = qty(BigDecimal.ZERO);
        line.unitPrice = money(unitPrice);
        if (line.unitPrice.signum() < 0) {
            throw new IllegalArgumentException("成交价不能为负数");
        }
        line.minPrice = minPrice == null ? null : money(minPrice);
        line.taxRate = taxRate;
        line.amount = money(line.quantity.multiply(line.unitPrice));
        line.serialIds = serialIds;
        line.remark = remark;
        line.createdAt = Instant.now();
        line.updatedAt = line.createdAt;
        return line;
    }

    public boolean belowMinPrice() {
        return minPrice != null && unitPrice.compareTo(minPrice) < 0;
    }

    /** 订单确认后、出库前补录或修正成交价，并同步行金额。 */
    public void updateUnitPrice(BigDecimal unitPrice) {
        BigDecimal next = money(unitPrice);
        if (next.signum() <= 0) {
            throw new IllegalArgumentException("成交价必须大于 0");
        }
        this.unitPrice = next;
        this.amount = money(quantity.multiply(next));
    }

    public void deliver(BigDecimal deliveredQuantity) {
        BigDecimal incoming = qty(deliveredQuantity);
        if (incoming.signum() <= 0) {
            throw new IllegalArgumentException("出库数量必须大于 0");
        }
        BigDecimal next = qty(this.deliveredQuantity.add(incoming));
        if (next.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("出库数量超过订单数量，剩余可出: "
                    + qty(quantity.subtract(this.deliveredQuantity)).toPlainString());
        }
        this.deliveredQuantity = next;
    }

    public BigDecimal remaining() {
        return qty(quantity.subtract(deliveredQuantity));
    }

    public boolean fullyDelivered() {
        return remaining().signum() == 0;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (deliveredQuantity == null) {
            deliveredQuantity = qty(BigDecimal.ZERO);
        }
        if (amount == null) {
            amount = money(quantity.multiply(unitPrice));
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

    private static BigDecimal qty(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public int getLineNo() { return lineNo; }
    public UUID getProductId() { return productId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getDeliveredQuantity() { return deliveredQuantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getAmount() { return amount; }
    public String getSerialIds() { return serialIds; }
    public String getRemark() { return remark; }
    public long getVersion() { return version; }
}
