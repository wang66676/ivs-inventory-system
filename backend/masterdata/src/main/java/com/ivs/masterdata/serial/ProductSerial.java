package com.ivs.masterdata.serial;

import com.ivs.masterdata.OwnershipType;
import com.ivs.masterdata.SerialStatus;
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
import java.time.LocalDate;
import java.util.UUID;

/**
 * 序列号档案：整车 VIN、动力电池 SN 一物一档。
 * 建档只负责身份与属性，库存数量与台账由 M2 库存模块负责。
 */
@Entity
@Table(name = "product_serial", schema = "ivs")
public class ProductSerial {

    @Id
    private UUID id;

    @Column(name = "serial_no", nullable = false, unique = true, length = 60)
    private String serialNo;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SerialStatus status;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "location_code", length = 60)
    private String locationCode;

    @Column(name = "batch_no", length = 60)
    private String batchNo;

    @Column(name = "purchase_price", precision = 18, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "inbound_at")
    private Instant inboundAt;

    @Column(name = "certificate_application_date")
    private LocalDate certificateApplicationDate;

    @Column(name = "warranty_start")
    private Instant warrantyStart;

    @Column(name = "warranty_end")
    private Instant warrantyEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_type", nullable = false, length = 20)
    private OwnershipType ownershipType;

    @Column(name = "lease_contract_id")
    private UUID leaseContractId;

    /** 序列号个性属性（JSON 字符串，按品类属性模板填写）。 */
    @Column(name = "attrs_json", columnDefinition = "text")
    private String attrsJson;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ProductSerial() {
    }

    public static ProductSerial create(String serialNo, UUID productId, UUID warehouseId, String locationCode,
                                       String batchNo, BigDecimal purchasePrice, Instant inboundAt,
                                       LocalDate certificateApplicationDate, Instant warrantyStart,
                                       Instant warrantyEnd, OwnershipType ownershipType,
                                       String attrsJson, String remark) {
        ProductSerial serial = new ProductSerial();
        serial.id = UUID.randomUUID();
        serial.serialNo = requireText(serialNo, "序列号不能为空").toUpperCase();
        if (productId == null) {
            throw new IllegalArgumentException("序列号必须归属商品");
        }
        serial.productId = productId;
        serial.status = inboundAt == null ? SerialStatus.PLANNED : SerialStatus.IN_STOCK;
        serial.warehouseId = warehouseId;
        serial.locationCode = normalize(locationCode);
        serial.batchNo = normalize(batchNo);
        serial.purchasePrice = money(purchasePrice);
        serial.inboundAt = inboundAt;
        serial.certificateApplicationDate = certificateApplicationDate;
        serial.warrantyStart = warrantyStart;
        serial.warrantyEnd = warrantyEnd;
        serial.ownershipType = ownershipType == null ? OwnershipType.OWNED : ownershipType;
        serial.attrsJson = normalize(attrsJson);
        serial.remark = normalize(remark);
        serial.createdAt = Instant.now();
        serial.updatedAt = serial.createdAt;
        return serial;
    }

    public void update(UUID warehouseId, String locationCode, String batchNo, BigDecimal purchasePrice,
                       Instant inboundAt, LocalDate certificateApplicationDate, Instant warrantyStart,
                       Instant warrantyEnd, OwnershipType ownershipType, String attrsJson, String remark) {
        this.warehouseId = warehouseId;
        this.locationCode = normalize(locationCode);
        this.batchNo = normalize(batchNo);
        this.purchasePrice = money(purchasePrice);
        this.inboundAt = inboundAt;
        this.certificateApplicationDate = certificateApplicationDate;
        this.warrantyStart = warrantyStart;
        this.warrantyEnd = warrantyEnd;
        if (ownershipType != null) {
            this.ownershipType = ownershipType;
        }
        this.attrsJson = normalize(attrsJson);
        this.remark = normalize(remark);
    }

    /** 收货入库回写：仓库、采购价与首次入库时间（首次入库时间只在为空时写入）。 */
    public void applyInbound(UUID warehouseId, BigDecimal inboundPrice, Instant inboundAt) {
        applyInbound(warehouseId, inboundPrice, inboundAt, null);
    }

    public void applyInbound(UUID warehouseId, BigDecimal inboundPrice, Instant inboundAt,
                             LocalDate certificateApplicationDate) {
        if (warehouseId != null) {
            this.warehouseId = warehouseId;
        }
        if (inboundPrice != null) {
            this.purchasePrice = money(inboundPrice);
        }
        if (this.inboundAt == null) {
            this.inboundAt = inboundAt == null ? Instant.now() : inboundAt;
        }
        if (certificateApplicationDate != null) {
            this.certificateApplicationDate = certificateApplicationDate;
        }
    }

    public void updateCertificateApplicationDate(LocalDate certificateApplicationDate) {
        this.certificateApplicationDate = certificateApplicationDate;
    }

    /** 按产品大项配置的状态机模板迁移状态，非法迁移直接拒绝。 */
    public void moveTo(SerialStatus target, SerialStatusTemplate template) {
        if (target == null) {
            throw new IllegalArgumentException("目标状态不能为空");
        }
        if (target == status) {
            return;
        }
        if (!template.allows(status, target)) {
            throw new IllegalStateException("状态不允许从 " + status + " 变更为 " + target
                    + "（模板 " + template.name() + " 允许：" + template.nextStatuses(status) + "）");
        }
        this.status = target;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = SerialStatus.PLANNED;
        }
        if (ownershipType == null) {
            ownershipType = OwnershipType.OWNED;
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

    public UUID getId() { return id; }
    public String getSerialNo() { return serialNo; }
    public UUID getProductId() { return productId; }
    public SerialStatus getStatus() { return status; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getLocationCode() { return locationCode; }
    public String getBatchNo() { return batchNo; }
    public BigDecimal getPurchasePrice() { return purchasePrice; }
    public Instant getInboundAt() { return inboundAt; }
    public LocalDate getCertificateApplicationDate() { return certificateApplicationDate; }
    public Instant getWarrantyStart() { return warrantyStart; }
    public Instant getWarrantyEnd() { return warrantyEnd; }
    public OwnershipType getOwnershipType() { return ownershipType; }

    public void changeOwnership(OwnershipType ownershipType) {
        if (ownershipType == null) {
            throw new IllegalArgumentException("所有权类型不能为空");
        }
        this.ownershipType = ownershipType;
    }
    public UUID getLeaseContractId() { return leaseContractId; }
    public void bindLeaseContract(UUID contractId) {
        this.leaseContractId = contractId;
    }
    public void clearLeaseContract() {
        this.leaseContractId = null;
    }
    public String getAttrsJson() { return attrsJson; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
