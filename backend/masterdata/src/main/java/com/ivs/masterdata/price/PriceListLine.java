package com.ivs.masterdata.price;

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

/** 价目表明细：一个商品一行，含成交价与最低限价。 */
@Entity
@Table(name = "price_list_line", schema = "ivs")
public class PriceListLine {

    @Id
    private UUID id;

    @Column(name = "price_list_id", nullable = false)
    private UUID priceListId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal price;

    @Column(name = "min_price", precision = 18, scale = 2)
    private BigDecimal minPrice;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PriceListLine() {
    }

    public static PriceListLine create(UUID priceListId, UUID productId, BigDecimal price, BigDecimal minPrice,
                                       String remark) {
        if (priceListId == null || productId == null) {
            throw new IllegalArgumentException("价目表明细必须归属价目表与商品");
        }
        PriceListLine line = new PriceListLine();
        line.id = UUID.randomUUID();
        line.priceListId = priceListId;
        line.productId = productId;
        line.price = money(price, "成交价不能为空");
        line.minPrice = minPrice == null ? null : money(minPrice, "最低限价不能为空");
        if (line.minPrice != null && line.minPrice.compareTo(line.price) > 0) {
            throw new IllegalArgumentException("最低限价不能高于成交价");
        }
        line.remark = remark;
        line.createdAt = Instant.now();
        line.updatedAt = line.createdAt;
        return line;
    }

    public void update(BigDecimal price, BigDecimal minPrice, String remark) {
        BigDecimal normalizedPrice = money(price, "成交价不能为空");
        BigDecimal normalizedMin = minPrice == null ? null : money(minPrice, "最低限价不能为空");
        if (normalizedMin != null && normalizedMin.compareTo(normalizedPrice) > 0) {
            throw new IllegalArgumentException("最低限价不能高于成交价");
        }
        this.price = normalizedPrice;
        this.minPrice = normalizedMin;
        this.remark = remark;
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

    private static BigDecimal money(BigDecimal value, String message) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("金额不能为负数");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getPriceListId() { return priceListId; }
    public UUID getProductId() { return productId; }
    public BigDecimal getPrice() { return price; }
    public BigDecimal getMinPrice() { return minPrice; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}