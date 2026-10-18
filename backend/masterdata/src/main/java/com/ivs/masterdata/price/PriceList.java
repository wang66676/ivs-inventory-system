package com.ivs.masterdata.price;

import com.ivs.masterdata.CustomerType;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** 价目表：按产品或客户类型给出成交价与最低限价，是「政策之外」的定价基础。 */
@Entity
@Table(name = "price_list", schema = "ivs")
public class PriceList {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", length = 20)
    private CustomerType customerType;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MasterStatus status;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PriceList() {
    }

    public static PriceList create(String code, String name, CustomerType customerType,
                                   LocalDate effectiveFrom, LocalDate effectiveTo, String remark) {
        PriceList priceList = new PriceList();
        priceList.id = UUID.randomUUID();
        priceList.code = requireText(code, "价目表编码不能为空").toUpperCase();
        priceList.name = requireText(name, "价目表名称不能为空");
        priceList.customerType = customerType;
        priceList.effectiveFrom = effectiveFrom;
        priceList.effectiveTo = effectiveTo;
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("失效日期不能早于生效日期");
        }
        priceList.remark = normalize(remark);
        priceList.status = MasterStatus.ACTIVE;
        priceList.createdAt = Instant.now();
        priceList.updatedAt = priceList.createdAt;
        return priceList;
    }

    public void update(String name, CustomerType customerType, LocalDate effectiveFrom, LocalDate effectiveTo,
                       String remark) {
        this.name = requireText(name, "价目表名称不能为空");
        this.customerType = customerType;
        if (effectiveFrom != null && effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("失效日期不能早于生效日期");
        }
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.remark = normalize(remark);
    }

    public boolean covers(LocalDate date) {
        if (date == null) {
            return true;
        }
        if (effectiveFrom != null && date.isBefore(effectiveFrom)) {
            return false;
        }
        return effectiveTo == null || !date.isAfter(effectiveTo);
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

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public CustomerType getCustomerType() { return customerType; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public LocalDate getEffectiveTo() { return effectiveTo; }
    public MasterStatus getStatus() { return status; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}