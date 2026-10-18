package com.ivs.retail.retail;

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
import java.time.ZoneId;
import java.util.UUID;

/**
 * 实销记录：整车完成终端零售（上牌）后的登记。
 *
 * <p>实销日期是政策返利的唯一时间基准；终端发票开票时间只作为必填事实与校验依据
 * （实销必须发生在开票之后）。</p>
 */
@Entity
@Table(name = "retail_record", schema = "ivs")
public class RetailRecord {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Id
    private UUID id;

    @Column(name = "retail_no", nullable = false, unique = true, length = 40)
    private String retailNo;

    @Column(name = "retail_date", nullable = false)
    private LocalDate retailDate;

    @Column(name = "invoice_issued_at", nullable = false)
    private Instant invoiceIssuedAt;

    @Column(name = "register_date")
    private LocalDate registerDate;

    @Column(name = "delivery_date")
    private LocalDate deliveryDate;

    @Column(name = "serial_id", nullable = false)
    private UUID serialId;

    @Column(name = "serial_no", nullable = false, length = 60)
    private String serialNo;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "user_name", nullable = false, length = 80)
    private String userName;

    @Column(name = "user_mobile", nullable = false, length = 40)
    private String userMobile;

    /** 车牌照号：选填。 */
    @Column(name = "plate_no", length = 40)
    private String plateNo;

    @Column(name = "sale_price", precision = 18, scale = 2)
    private BigDecimal salePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RetailStatus status;

    /** 有效实销的唯一键（= serialId）；冲正后置空，从而允许同一序列号重新登记。 */
    @Column(name = "active_serial_key")
    private UUID activeSerialKey;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected RetailRecord() {
    }

    public static RetailRecord create(String retailNo, LocalDate retailDate, Instant invoiceIssuedAt,
                                      LocalDate registerDate, LocalDate deliveryDate, UUID serialId, String serialNo,
                                      UUID productId, UUID customerId, UUID orderId, String userName,
                                      String userMobile, String plateNo, BigDecimal salePrice, String remark) {
        RetailRecord record = new RetailRecord();
        record.id = UUID.randomUUID();
        record.retailNo = retailNo;
        record.retailDate = retailDate;
        record.invoiceIssuedAt = invoiceIssuedAt;
        record.registerDate = registerDate;
        record.deliveryDate = deliveryDate;
        record.serialId = serialId;
        record.serialNo = serialNo;
        record.productId = productId;
        record.customerId = customerId;
        record.orderId = orderId;
        record.userName = requireText(userName, "车辆使用人不能为空");
        record.userMobile = requireText(userMobile, "手机号不能为空");
        record.plateNo = normalize(plateNo);
        record.salePrice = salePrice == null ? null : salePrice.setScale(2, RoundingMode.HALF_UP);
        record.status = RetailStatus.DRAFT;
        record.activeSerialKey = serialId;
        record.remark = normalize(remark);
        record.createdAt = Instant.now();
        record.updatedAt = record.createdAt;
        record.validateDates();
        return record;
    }

    /** 实销日期必须晚于（或等于）终端发票开票时间。 */
    public void validateDates() {
        if (retailDate == null) {
            throw new IllegalArgumentException("实销日期不能为空");
        }
        if (invoiceIssuedAt == null) {
            throw new IllegalArgumentException("终端发票开票时间不能为空");
        }
        LocalDate invoiceDate = invoiceIssuedAt.atZone(ZONE).toLocalDate();
        if (retailDate.isBefore(invoiceDate)) {
            throw new IllegalArgumentException("实销日期不能早于终端发票开票时间（开票日 " + invoiceDate + "）");
        }
    }

    public void update(LocalDate retailDate, Instant invoiceIssuedAt, LocalDate registerDate, LocalDate deliveryDate,
                       String userName, String userMobile, String plateNo, BigDecimal salePrice, String remark) {
        if (status == RetailStatus.VERIFIED) {
            throw new IllegalStateException("已复核的实销记录不能直接修改，请先冲正");
        }
        this.retailDate = retailDate;
        this.invoiceIssuedAt = invoiceIssuedAt;
        this.registerDate = registerDate;
        this.deliveryDate = deliveryDate;
        this.userName = requireText(userName, "车辆使用人不能为空");
        this.userMobile = requireText(userMobile, "手机号不能为空");
        this.plateNo = normalize(plateNo);
        this.salePrice = salePrice == null ? null : salePrice.setScale(2, RoundingMode.HALF_UP);
        this.remark = normalize(remark);
        validateDates();
    }

    public void submit() {
        if (status == RetailStatus.SUBMITTED) {
            return;
        }
        if (status != RetailStatus.DRAFT) {
            throw new IllegalStateException("只有草稿实销记录可以提交复核");
        }
        this.status = RetailStatus.SUBMITTED;
    }

    /** 复核确认：确认后成为政策兑现基准，只能通过冲正修改。 */
    public void verify(UUID operatorId) {
        if (status == RetailStatus.VERIFIED) {
            return;
        }
        if (status != RetailStatus.SUBMITTED) {
            throw new IllegalStateException("只有已提交的实销记录可以复核");
        }
        this.status = RetailStatus.VERIFIED;
        this.verifiedBy = operatorId;
        this.verifiedAt = Instant.now();
    }

    /** 冲正：退车 / 退牌 / 换车；释放序列号占用，允许重新登记。 */
    public void reverse() {
        if (status == RetailStatus.REVERSED) {
            return;
        }
        this.status = RetailStatus.REVERSED;
        this.activeSerialKey = null;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = RetailStatus.DRAFT;
        if (activeSerialKey == null && status != RetailStatus.REVERSED) activeSerialKey = serialId;
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = Instant.now(); }

    private static String requireText(String value, String message) {
        String normalized = normalize(value);
        if (normalized == null) throw new IllegalArgumentException(message);
        return normalized;
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public UUID getId() { return id; }
    public String getRetailNo() { return retailNo; }
    public LocalDate getRetailDate() { return retailDate; }
    public Instant getInvoiceIssuedAt() { return invoiceIssuedAt; }
    public LocalDate getRegisterDate() { return registerDate; }
    public LocalDate getDeliveryDate() { return deliveryDate; }
    public UUID getSerialId() { return serialId; }
    public String getSerialNo() { return serialNo; }
    public UUID getProductId() { return productId; }
    public UUID getCustomerId() { return customerId; }
    public UUID getOrderId() { return orderId; }
    public String getUserName() { return userName; }
    public String getUserMobile() { return userMobile; }
    public String getPlateNo() { return plateNo; }
    public BigDecimal getSalePrice() { return salePrice; }
    public RetailStatus getStatus() { return status; }
    public UUID getActiveSerialKey() { return activeSerialKey; }
    public UUID getVerifiedBy() { return verifiedBy; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}