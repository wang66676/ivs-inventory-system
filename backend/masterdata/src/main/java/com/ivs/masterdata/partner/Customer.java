package com.ivs.masterdata.partner;

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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 客户：个人、单位、大客户（车队）。 */
@Entity
@Table(name = "customer", schema = "ivs")
public class Customer {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;

    @Column(name = "contact_name", length = 80)
    private String contactName;

    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    @Column(name = "tax_no", length = 64)
    private String taxNo;

    @Column(name = "credit_limit", nullable = false, precision = 18, scale = 2)
    private BigDecimal creditLimit;

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

    protected Customer() {
    }

    public static Customer create(String code, String name, CustomerType customerType, String contactName,
                                  String contactPhone, String taxNo, BigDecimal creditLimit, String remark) {
        Customer customer = new Customer();
        customer.id = UUID.randomUUID();
        customer.code = requireText(code, "客户编码不能为空").toUpperCase();
        customer.name = requireText(name, "客户名称不能为空");
        customer.customerType = customerType == null ? CustomerType.INDIVIDUAL : customerType;
        customer.contactName = normalize(contactName);
        customer.contactPhone = normalize(contactPhone);
        customer.taxNo = normalize(taxNo);
        customer.creditLimit = credit(creditLimit);
        customer.remark = normalize(remark);
        customer.status = MasterStatus.ACTIVE;
        customer.createdAt = Instant.now();
        customer.updatedAt = customer.createdAt;
        return customer;
    }

    public void update(String name, CustomerType customerType, String contactName, String contactPhone,
                       String taxNo, BigDecimal creditLimit, String remark) {
        this.name = requireText(name, "客户名称不能为空");
        this.customerType = customerType == null ? this.customerType : customerType;
        this.contactName = normalize(contactName);
        this.contactPhone = normalize(contactPhone);
        this.taxNo = normalize(taxNo);
        this.creditLimit = credit(creditLimit);
        this.remark = normalize(remark);
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
        if (creditLimit == null) {
            creditLimit = BigDecimal.ZERO.setScale(2);
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

    private static BigDecimal credit(BigDecimal value) {
        BigDecimal normalized = value == null ? BigDecimal.ZERO : value;
        if (normalized.signum() < 0) {
            throw new IllegalArgumentException("信用额度不能为负数");
        }
        return normalized.setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public CustomerType getCustomerType() { return customerType; }
    public String getContactName() { return contactName; }
    public String getContactPhone() { return contactPhone; }
    public String getTaxNo() { return taxNo; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public String getRemark() { return remark; }
    public MasterStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}