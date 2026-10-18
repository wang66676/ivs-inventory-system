package com.ivs.inventory.transfer;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 调拨明细：数量、成本快照与签收差异。 */
@Entity
@Table(name = "stock_transfer_line", schema = "ivs")
public class StockTransferLine {

    @Id private UUID id;
    @Column(name = "transfer_id", nullable = false) private UUID transferId;
    @Column(name = "product_id", nullable = false) private UUID productId;
    @Column(name = "batch_no", nullable = false, length = 60) private String batchNo;
    @Column(nullable = false, precision = 18, scale = 3) private BigDecimal quantity;
    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 3) private BigDecimal receivedQuantity;
    @Column(name = "difference_quantity", nullable = false, precision = 18, scale = 3) private BigDecimal differenceQuantity;
    @Column(name = "unit_cost", nullable = false, precision = 18, scale = 2) private BigDecimal unitCost;
    @Column(name = "difference_reason", length = 300) private String differenceReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected StockTransferLine() {
    }

    public static StockTransferLine create(UUID transferId, UUID productId, String batchNo,
                                           BigDecimal quantity, BigDecimal unitCost) {
        StockTransferLine line = new StockTransferLine();
        line.id = UUID.randomUUID();
        line.transferId = transferId;
        line.productId = productId;
        line.batchNo = normalizeBatch(batchNo);
        line.quantity = qty(quantity);
        if (line.quantity.signum() <= 0) throw new IllegalArgumentException("调拨数量必须大于 0");
        line.receivedQuantity = qty(BigDecimal.ZERO);
        line.differenceQuantity = qty(BigDecimal.ZERO);
        line.unitCost = money(unitCost);
        line.createdAt = Instant.now();
        line.updatedAt = line.createdAt;
        return line;
    }

    public void dispatchedAtCost(BigDecimal actualUnitCost) {
        this.unitCost = money(actualUnitCost);
    }

    public void receive(BigDecimal actual, String reason) {
        this.receivedQuantity = qty(actual);
        if (receivedQuantity.signum() < 0 || receivedQuantity.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("签收数量不能为负且不能大于调拨数量");
        }
        this.differenceQuantity = qty(quantity.subtract(receivedQuantity));
        if (differenceQuantity.signum() != 0) {
            if (reason == null || reason.isBlank()) throw new IllegalArgumentException("存在签收差异时必须填写原因");
            this.differenceReason = reason.trim();
        }
        this.updatedAt = Instant.now();
    }

    private static String normalizeBatch(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
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
        if (quantity == null) quantity = qty(BigDecimal.ZERO);
        if (receivedQuantity == null) receivedQuantity = qty(BigDecimal.ZERO);
        if (differenceQuantity == null) differenceQuantity = qty(BigDecimal.ZERO);
        if (unitCost == null) unitCost = money(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getTransferId() { return transferId; }
    public UUID getProductId() { return productId; }
    public String getBatchNo() { return batchNo; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public BigDecimal getDifferenceQuantity() { return differenceQuantity; }
    public BigDecimal getUnitCost() { return unitCost; }
    public String getDifferenceReason() { return differenceReason; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
