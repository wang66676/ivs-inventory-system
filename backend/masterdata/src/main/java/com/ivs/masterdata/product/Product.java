package com.ivs.masterdata.product;

import com.ivs.masterdata.MasterStatus;
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

/** 商品（车型 / SKU）：整车按车型配置建档，电池与配件同理。 */
@Entity
@Table(name = "product", schema = "ivs")
public class Product {

    @Id
    private UUID id;

    @Column(name = "product_no", nullable = false, unique = true, length = 40)
    private String productNo;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(length = 80)
    private String brand;

    @Column(length = 80)
    private String series;

    @Column(length = 120)
    private String model;

    @Column(name = "config_code", length = 100)
    private String configCode;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "tax_rate", precision = 8, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "min_price", precision = 18, scale = 2)
    private BigDecimal minPrice;

    @Column(name = "sale_price_guide", precision = 18, scale = 2)
    private BigDecimal salePriceGuide;

    @Column(name = "purchase_price_ref", precision = 18, scale = 2)
    private BigDecimal purchasePriceRef;

    /** 品类特有属性（JSON 字符串，生产环境可迁移为 jsonb）。 */
    @Column(name = "spec_json", columnDefinition = "text")
    private String specJson;

    @Column(length = 300)
    private String remark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MasterStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Product() {
    }

    public static Product create(String productNo, String name, UUID categoryId, String unit,
                                 String brand, String series, String model, String configCode,
                                 BigDecimal taxRate, BigDecimal minPrice, BigDecimal salePriceGuide,
                                 BigDecimal purchasePriceRef, String specJson, String remark) {
        Product product = new Product();
        product.id = UUID.randomUUID();
        product.productNo = requireText(productNo, "商品编码不能为空");
        product.name = requireText(name, "商品名称不能为空");
        if (categoryId == null) {
            throw new IllegalArgumentException("商品必须归属产品大项");
        }
        product.categoryId = categoryId;
        product.unit = normalize(unit) == null ? "件" : unit.trim();
        product.brand = normalize(brand);
        product.series = normalize(series);
        product.model = normalize(model);
        product.configCode = normalize(configCode);
        product.taxRate = rate(taxRate);
        product.minPrice = money(minPrice);
        product.salePriceGuide = money(salePriceGuide);
        product.purchasePriceRef = money(purchasePriceRef);
        product.specJson = normalize(specJson);
        product.remark = normalize(remark);
        product.status = MasterStatus.ACTIVE;
        product.createdAt = Instant.now();
        product.updatedAt = product.createdAt;
        return product;
    }

    public void update(String name, String unit, String brand, String series, String model, String configCode,
                       BigDecimal taxRate, BigDecimal minPrice, BigDecimal salePriceGuide,
                       BigDecimal purchasePriceRef, String specJson, String remark) {
        this.name = requireText(name, "商品名称不能为空");
        this.unit = normalize(unit) == null ? this.unit : unit.trim();
        this.brand = normalize(brand);
        this.series = normalize(series);
        this.model = normalize(model);
        this.configCode = normalize(configCode);
        this.taxRate = rate(taxRate);
        this.minPrice = money(minPrice);
        this.salePriceGuide = money(salePriceGuide);
        this.purchasePriceRef = money(purchasePriceRef);
        this.specJson = normalize(specJson);
        this.remark = normalize(remark);
    }

    /** 低于最低限价的成交需要审批（M3 使用），此处提供统一判定。 */
    public boolean belowMinPrice(BigDecimal price) {
        return price != null && minPrice != null && price.compareTo(minPrice) < 0;
    }

    public void disable() { this.status = MasterStatus.DISABLED; }
    public void enable() { this.status = MasterStatus.ACTIVE; }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = MasterStatus.ACTIVE;
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

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal rate(BigDecimal value) {
        return value == null ? null : value.setScale(4, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getProductNo() { return productNo; }
    public String getName() { return name; }
    public UUID getCategoryId() { return categoryId; }
    public String getBrand() { return brand; }
    public String getSeries() { return series; }
    public String getModel() { return model; }
    public String getConfigCode() { return configCode; }
    public String getUnit() { return unit; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getMinPrice() { return minPrice; }
    public BigDecimal getSalePriceGuide() { return salePriceGuide; }
    public BigDecimal getPurchasePriceRef() { return purchasePriceRef; }
    public String getSpecJson() { return specJson; }
    public String getRemark() { return remark; }
    public MasterStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}