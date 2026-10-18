package com.ivs.procurement.purchase;

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

/** 采购明细行。 */
@Entity
@Table(name = "purchase_order_line", schema = "ivs")
public class PurchaseOrderLine {

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

    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQuantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "tax_rate", precision = 8, scale = 4)
    private BigDecimal taxRate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PurchaseOrderLine() {
    }

    public static PurchaseOrderLine create(UUID orderId, int lineNo, UUID productId, BigDecimal quantity,
                                           BigDecimal unitPrice, BigDecimal taxRate, String remark) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.id = UUID.randomUUID();
        line.orderId = orderId;
        line.lineNo = lineNo;
        line.productId = productId;
        line.quantity = qty(quantity);
        if (line.quantity.signum() <= 0) {
            throw new IllegalArgumentException("采购数量必须大于 0");
        }
        line.receivedQuantity = qty(BigDecimal.ZERO);
        line.unitPrice = money(unitPrice);
        if (line.unitPrice.signum() < 0) {
            throw new IllegalArgumentException("采购单价不能为负数");
        }
        line.taxRate = taxRate;
        line.amount = money(line.quantity.multiply(line.unitPrice));
        line.remark = remark;
        line.createdAt = Instant.now();
        line.updatedAt = line.createdAt;
        return line;
    }

    public void receive(BigDecimal receivedQuantity) {
        BigDecimal incoming = qty(receivedQuantity);
        if (incoming.signum() <= 0) {
            throw new IllegalArgumentException("收货数量必须大于 0");
        }
        BigDecimal next = qty(this.receivedQuantity.add(incoming));
        if (next.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("收货数量超过采购数量，剩余可收: "
                    + qty(this.quantity.subtract(this.receivedQuantity)).toPlainString());
        }
        this.receivedQuantity = next;
    }

    public BigDecimal remaining() {
        return qty(quantity.subtract(receivedQuantity));
    }

    public boolean fullyReceived() {
        return remaining().signum() == 0;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (receivedQuantity == null) {
            receivedQuantity = qty(BigDecimal.ZERO);
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
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getAmount() { return amount; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}