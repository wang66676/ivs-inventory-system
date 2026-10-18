package com.ivs.masterdata.partner;

import com.ivs.masterdata.MasterStatus;
import com.ivs.masterdata.SupplierType;
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
import java.util.UUID;

/** 供应商：整车厂标记为 OEM，是返利申报、红字发票与返利资金池的主体。 */
@Entity
@Table(name = "supplier", schema = "ivs")
public class Supplier {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 60)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "supplier_type", nullable = false, length = 20)
    private SupplierType supplierType;

    @Column(name = "contact_name", length = 80)
    private String contactName;

    @Column(name = "contact_phone", length = 40)
    private String contactPhone;

    @Column(name = "tax_no", length = 64)
    private String taxNo;

    @Column(name = "payment_terms", length = 120)
    private String paymentTerms;

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

    protected Supplier() {
    }

    public static Supplier create(String code, String name, SupplierType supplierType, String contactName,
                                  String contactPhone, String taxNo, String paymentTerms, String remark) {
        Supplier supplier = new Supplier();
        supplier.id = UUID.randomUUID();
        supplier.code = requireText(code, "供应商编码不能为空").toUpperCase();
        supplier.name = requireText(name, "供应商名称不能为空");
        supplier.supplierType = supplierType == null ? SupplierType.OTHER : supplierType;
        supplier.contactName = normalize(contactName);
        supplier.contactPhone = normalize(contactPhone);
        supplier.taxNo = normalize(taxNo);
        supplier.paymentTerms = normalize(paymentTerms);
        supplier.remark = normalize(remark);
        supplier.status = MasterStatus.ACTIVE;
        supplier.createdAt = Instant.now();
        supplier.updatedAt = supplier.createdAt;
        return supplier;
    }

    public void update(String name, SupplierType supplierType, String contactName, String contactPhone,
                       String taxNo, String paymentTerms, String remark) {
        this.name = requireText(name, "供应商名称不能为空");
        this.supplierType = supplierType == null ? this.supplierType : supplierType;
        this.contactName = normalize(contactName);
        this.contactPhone = normalize(contactPhone);
        this.taxNo = normalize(taxNo);
        this.paymentTerms = normalize(paymentTerms);
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
    public SupplierType getSupplierType() { return supplierType; }
    public String getContactName() { return contactName; }
    public String getContactPhone() { return contactPhone; }
    public String getTaxNo() { return taxNo; }
    public String getPaymentTerms() { return paymentTerms; }
    public String getRemark() { return remark; }
    public MasterStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}