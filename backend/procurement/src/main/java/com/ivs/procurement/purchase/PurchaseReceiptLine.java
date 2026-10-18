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

/** 收货明细：数量商品只填数量，序列号商品以 serial_nos 记录本次到店的 VIN / 电池 SN。 */
@Entity
@Table(name = "purchase_receipt_line", schema = "ivs")
public class PurchaseReceiptLine {

    @Id
    private UUID id;

    @Column(name = "receipt_id", nullable = false)
    private UUID receiptId;

    @Column(name = "order_line_id")
    private UUID orderLineId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "batch_no", length = 60)
    private String batchNo;

    /** JSON 数组字符串，例如 ["LZZ1CLWB0NA000001"]。 */
    @Column(name = "serial_nos", columnDefinition = "text")
    private String serialNos;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PurchaseReceiptLine() {
    }

    public static PurchaseReceiptLine create(UUID receiptId, UUID orderLineId, UUID productId,
                                             BigDecimal quantity, BigDecimal unitPrice, String batchNo,
                                             String serialNos, String remark) {
        PurchaseReceiptLine line = new PurchaseReceiptLine();
        line.id = UUID.randomUUID();
        line.receiptId = receiptId;
        line.orderLineId = orderLineId;
        line.productId = productId;
        line.quantity = (quantity == null ? BigDecimal.ZERO : quantity).setScale(3, RoundingMode.HALF_UP);
        line.unitPrice = (unitPrice == null ? BigDecimal.ZERO : unitPrice).setScale(2, RoundingMode.HALF_UP);
        line.batchNo = batchNo;
        line.serialNos = serialNos;
        line.remark = remark;
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
    public UUID getReceiptId() { return receiptId; }
    public UUID getOrderLineId() { return orderLineId; }
    public UUID getProductId() { return productId; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getBatchNo() { return batchNo; }
    public String getSerialNos() { return serialNos; }
    public String getRemark() { return remark; }
    public long getVersion() { return version; }
}