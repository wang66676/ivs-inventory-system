package com.ivs.procurement.returns;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 采购退货明细。 */
@Entity
@Table(name = "purchase_return_line", schema = "ivs")
public class PurchaseReturnLine {

    @Id private UUID id;
    @Column(name = "return_id", nullable = false) private UUID returnId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(nullable = false, precision = 18, scale = 3) private BigDecimal quantity;
    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2) private BigDecimal unitPrice;
    @Column(nullable = false, precision = 18, scale = 2) private BigDecimal amount;
    @Column(name = "batch_no", length = 60) private String batchNo;
    @Column(name = "serial_nos") private String serialNos;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected PurchaseReturnLine() {
    }

    public static PurchaseReturnLine create(UUID returnId, UUID productId, BigDecimal quantity,
                                            BigDecimal unitPrice, String batchNo, String serialNos) {
        PurchaseReturnLine line = new PurchaseReturnLine();
        line.id = UUID.randomUUID();
        line.returnId = returnId;
        line.productId = productId;
        line.quantity = qty(quantity);
        if (line.quantity.signum() <= 0) throw new IllegalArgumentException("退货数量必须大于 0");
        line.unitPrice = money(unitPrice);
        if (line.unitPrice.signum() < 0) throw new IllegalArgumentException("退货单价不能为负");
        line.amount = money(line.quantity.multiply(line.unitPrice));
        line.batchNo = batchNo == null || batchNo.isBlank() ? "-" : batchNo.trim();
        line.serialNos = serialNos;
        line.createdAt = Instant.now();
        line.updatedAt = line.createdAt;
        return line;
    }

    private static BigDecimal qty(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }
    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getReturnId() { return returnId; }
    public UUID getProductId() { return productId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getAmount() { return amount; }
    public String getBatchNo() { return batchNo; }
    public String getSerialNos() { return serialNos; }
    public Instant getCreatedAt() { return createdAt; }
}
