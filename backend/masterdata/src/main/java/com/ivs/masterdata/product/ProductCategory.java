package com.ivs.masterdata.product;

import com.ivs.masterdata.MasterStatus;
import com.ivs.masterdata.SerialStatusTemplate;
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

/**
 * 产品大项：整车、动力电池、配件、其他。
 *
 * <p>新增产品大项是配置动作：选择库存追踪方式（数量/序列号）与状态机模板即可，
 * 不需要修改代码或表结构。</p>
 */
@Entity
@Table(name = "product_category", schema = "ivs")
public class ProductCategory {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "tracking_mode", nullable = false, length = 20)
    private TrackingMode trackingMode;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "default_tax_rate", precision = 8, scale = 4)
    private BigDecimal defaultTaxRate;

    @Column(name = "status_template", nullable = false, length = 40)
    private String statusTemplate;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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

    protected ProductCategory() {
    }

    public static ProductCategory create(String code, String name, TrackingMode trackingMode, String unit,
                                         BigDecimal defaultTaxRate, String statusTemplate, int sortOrder,
                                         String remark) {
        ProductCategory category = new ProductCategory();
        category.id = UUID.randomUUID();
        category.code = requireText(code, "产品大项编码不能为空").toUpperCase();
        category.name = requireText(name, "产品大项名称不能为空");
        category.trackingMode = trackingMode == null ? TrackingMode.QUANTITY : trackingMode;
        category.unit = normalize(unit) == null ? "件" : unit.trim();
        category.defaultTaxRate = rate(defaultTaxRate);
        category.statusTemplate = category.trackingMode == TrackingMode.SERIAL
                ? SerialStatusTemplate.requireOf(statusTemplate).name()
                : SerialStatusTemplate.requireOf(statusTemplate == null ? "SIMPLE" : statusTemplate).name();
        category.sortOrder = sortOrder;
        category.remark = normalize(remark);
        category.status = MasterStatus.ACTIVE;
        category.createdAt = Instant.now();
        category.updatedAt = category.createdAt;
        return category;
    }

    /** 更新基础信息；库存追踪方式一旦被商品引用则不允许修改（由服务层校验）。 */
    public void update(String name, TrackingMode trackingMode, String unit, BigDecimal defaultTaxRate,
                       String statusTemplate, int sortOrder, String remark) {
        this.name = requireText(name, "产品大项名称不能为空");
        this.trackingMode = trackingMode == null ? this.trackingMode : trackingMode;
        this.unit = normalize(unit) == null ? this.unit : unit.trim();
        this.defaultTaxRate = rate(defaultTaxRate);
        this.statusTemplate = this.trackingMode == TrackingMode.SERIAL
                ? SerialStatusTemplate.requireOf(statusTemplate).name()
                : this.statusTemplate;
        this.sortOrder = sortOrder;
        this.remark = normalize(remark);
    }

    public void disable() {
        this.status = MasterStatus.DISABLED;
    }

    public void enable() {
        this.status = MasterStatus.ACTIVE;
    }

    public boolean managesSerials() {
        return trackingMode == TrackingMode.SERIAL;
    }

    public SerialStatusTemplate statusTemplate() {
        return SerialStatusTemplate.valueOf(statusTemplate);
    }

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

    private static BigDecimal rate(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0) {
            throw new IllegalArgumentException("税率不能为负数");
        }
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public TrackingMode getTrackingMode() { return trackingMode; }
    public String getUnit() { return unit; }
    public BigDecimal getDefaultTaxRate() { return defaultTaxRate; }
    public String getStatusTemplate() { return statusTemplate; }
    public int getSortOrder() { return sortOrder; }
    public String getRemark() { return remark; }
    public MasterStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}