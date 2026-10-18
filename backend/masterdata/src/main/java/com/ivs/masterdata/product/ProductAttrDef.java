package com.ivs.masterdata.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * 产品大项的属性模板：驱动商品表单与序列号档案的扩展字段，
 * 是「新增产品大项不改代码」的关键机制。
 */
@Entity
@Table(name = "product_attr_def", schema = "ivs")
public class ProductAttrDef {

    @Id
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 60)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "options_json", columnDefinition = "text")
    private String optionsJson;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ProductAttrDef() {
    }

    public static ProductAttrDef create(UUID categoryId, String code, String name, String dataType,
                                        boolean required, String optionsJson, int sortOrder) {
        if (categoryId == null) {
            throw new IllegalArgumentException("属性定义必须归属产品大项");
        }
        ProductAttrDef def = new ProductAttrDef();
        def.id = UUID.randomUUID();
        def.categoryId = categoryId;
        def.code = requireText(code, "属性编码不能为空");
        def.name = requireText(name, "属性名称不能为空");
        def.dataType = normalize(dataType) == null ? "STRING" : dataType.trim().toUpperCase();
        def.required = required;
        def.optionsJson = normalize(optionsJson);
        def.sortOrder = sortOrder;
        def.createdAt = Instant.now();
        def.updatedAt = def.createdAt;
        return def;
    }

    public void update(String name, String dataType, boolean required, String optionsJson, int sortOrder) {
        this.name = requireText(name, "属性名称不能为空");
        this.dataType = normalize(dataType) == null ? this.dataType : dataType.trim().toUpperCase();
        this.required = required;
        this.optionsJson = normalize(optionsJson);
        this.sortOrder = sortOrder;
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

    public UUID getId() { return id; }
    public UUID getCategoryId() { return categoryId; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDataType() { return dataType; }
    public boolean isRequired() { return required; }
    public String getOptionsJson() { return optionsJson; }
    public int getSortOrder() { return sortOrder; }
    public long getVersion() { return version; }
}