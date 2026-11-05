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
import java.time.LocalDate;
import java.util.UUID;

/** 采购明细行。 */
@Entity
@Table(name = "purchase_order_line", schema = "ivs")
public class PurchaseOrderLine {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** 采购明细人工填写的配置文本。 */
    @Column(name = "config_text", length = 200)
    private String configText;

    /** 该明细计划的收货仓库，可在收货时按 VIN 覆盖。 */
    @Column(name = "warehouse_id")
    private UUID warehouseId;

    /** 一车一 VIN：采购明细按 VIN/SN 逐台录入。 */
    @Column(name = "serial_no", length = 60)
    private String serialNo;

    @Column(name = "certificate_application_date")
    private LocalDate certificateApplicationDate;

    /** 电池采购明细预计到货时间。 */
    @Column(name = "arrival_date")
    private LocalDate arrivalDate;

    @Column(nullable = false, precision = 18, scale = 3)
    private BigDecimal quantity;

    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 3)
    private BigDecimal receivedQuantity;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 2)
    private BigDecimal unitPrice;

    /** 动力电池箱信息；车辆与其它商品为空。 */
    @Column(name = "battery_box_manufacturer", length = 200)
    private String batteryBoxManufacturer;

    @Column(name = "battery_box_model", length = 160)
    private String batteryBoxModel;

    @Column(name = "battery_box_unit_price", precision = 18, scale = 2)
    private BigDecimal batteryBoxUnitPrice;

    /** 动力电池包信息；一整块电池由一个电池箱与一个电池包组成。 */
    @Column(name = "battery_pack_manufacturer", length = 200)
    private String batteryPackManufacturer;

    @Column(name = "battery_energy_kwh", precision = 18, scale = 3)
    private BigDecimal batteryEnergyKwh;

    @Column(name = "battery_pack_unit_price", precision = 18, scale = 2)
    private BigDecimal batteryPackUnitPrice;

    @Column(name = "tax_rate", precision = 8, scale = 4)
    private BigDecimal taxRate;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(length = 300)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PurchaseOrderLine() {
    }

    public static PurchaseOrderLine create(UUID orderId, int lineNo, UUID productId, BigDecimal quantity,
                                           BigDecimal unitPrice, BigDecimal taxRate, String configText,
                                           UUID warehouseId, String remark) {
        return create(orderId, lineNo, productId, quantity, unitPrice, taxRate, configText, warehouseId,
                null, null, remark);
    }

    public static PurchaseOrderLine create(UUID orderId, int lineNo, UUID productId, BigDecimal quantity,
                                           BigDecimal unitPrice, BigDecimal taxRate, String configText,
                                           UUID warehouseId, String serialNo, LocalDate certificateApplicationDate,
                                           String remark) {
        return create(orderId, lineNo, productId, quantity, unitPrice, taxRate, configText, warehouseId,
                serialNo, certificateApplicationDate, remark, null, null, null, null, null, null);
    }

    public static PurchaseOrderLine create(UUID orderId, int lineNo, UUID productId, BigDecimal quantity,
                                           BigDecimal unitPrice, BigDecimal taxRate, String configText,
                                           UUID warehouseId, String serialNo, LocalDate certificateApplicationDate,
                                           String remark, String batteryBoxManufacturer, String batteryBoxModel,
                                           BigDecimal batteryBoxUnitPrice, String batteryPackManufacturer,
                                           BigDecimal batteryEnergyKwh, BigDecimal batteryPackUnitPrice) {
        return create(orderId, lineNo, productId, quantity, unitPrice, taxRate, configText, warehouseId,
                serialNo, certificateApplicationDate, null, remark, batteryBoxManufacturer, batteryBoxModel,
                batteryBoxUnitPrice, batteryPackManufacturer, batteryEnergyKwh, batteryPackUnitPrice);
    }

    public static PurchaseOrderLine create(UUID orderId, int lineNo, UUID productId, BigDecimal quantity,
                                           BigDecimal unitPrice, BigDecimal taxRate, String configText,
                                           UUID warehouseId, String serialNo, LocalDate certificateApplicationDate,
                                           LocalDate arrivalDate, String remark, String batteryBoxManufacturer,
                                           String batteryBoxModel, BigDecimal batteryBoxUnitPrice,
                                           String batteryPackManufacturer, BigDecimal batteryEnergyKwh,
                                           BigDecimal batteryPackUnitPrice) {
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.id = UUID.randomUUID();
        line.orderId = orderId;
        line.lineNo = lineNo;
        line.productId = productId;
        line.configText = normalize(configText);
        line.warehouseId = warehouseId;
        line.serialNo = normalize(serialNo == null ? null : serialNo.toUpperCase());
        line.certificateApplicationDate = certificateApplicationDate;
        line.arrivalDate = arrivalDate;
        line.quantity = qty(quantity);
        if (line.quantity.signum() <= 0) {
            throw new IllegalArgumentException("采购数量必须大于 0");
        }
        line.receivedQuantity = qty(BigDecimal.ZERO);
        line.unitPrice = money(unitPrice);
        if (line.unitPrice.signum() < 0) {
            throw new IllegalArgumentException("采购单价不能为负数");
        }
        line.batteryBoxManufacturer = normalize(batteryBoxManufacturer);
        line.batteryBoxModel = normalize(batteryBoxModel);
        line.batteryBoxUnitPrice = batteryBoxUnitPrice == null ? null : money(batteryBoxUnitPrice);
        line.batteryPackManufacturer = normalize(batteryPackManufacturer);
        line.batteryEnergyKwh = batteryEnergyKwh == null ? null : qty(batteryEnergyKwh);
        line.batteryPackUnitPrice = batteryPackUnitPrice == null ? null : money(batteryPackUnitPrice);
        line.taxRate = taxRate;
        line.amount = money(line.quantity.multiply(line.unitPrice));
        line.remark = remark;
        line.createdAt = Instant.now();
        line.updatedAt = line.createdAt;
        return line;
    }

    public void receive(BigDecimal receivedQuantity) {
        BigDecimal incoming = qty(receivedQuantity);
        if (incoming.signum() <= 0) {
            throw new IllegalArgumentException("收货数量必须大于 0");
        }
        BigDecimal next = qty(this.receivedQuantity.add(incoming));
        if (next.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("收货数量超过采购数量，剩余可收: "
                    + qty(this.quantity.subtract(this.receivedQuantity)).toPlainString());
        }
        this.receivedQuantity = next;
    }

    public BigDecimal remaining() {
        return qty(quantity.subtract(receivedQuantity));
    }

    public boolean fullyReceived() {
        return remaining().signum() == 0;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (receivedQuantity == null) {
            receivedQuantity = qty(BigDecimal.ZERO);
        }
        if (amount == null) {
            amount = money(quantity.multiply(unitPrice));
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

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BigDecimal qty(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public UUID getOrderId() { return orderId; }
    public int getLineNo() { return lineNo; }
    public UUID getProductId() { return productId; }
    public String getConfigText() { return configText; }
    public UUID getWarehouseId() { return warehouseId; }
    public String getSerialNo() { return serialNo; }
    public LocalDate getCertificateApplicationDate() { return certificateApplicationDate; }
    public LocalDate getArrivalDate() { return arrivalDate; }
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public String getBatteryBoxManufacturer() { return batteryBoxManufacturer; }
    public String getBatteryBoxModel() { return batteryBoxModel; }
    public BigDecimal getBatteryBoxUnitPrice() { return batteryBoxUnitPrice; }
    public String getBatteryPackManufacturer() { return batteryPackManufacturer; }
    public BigDecimal getBatteryEnergyKwh() { return batteryEnergyKwh; }
    public BigDecimal getBatteryPackUnitPrice() { return batteryPackUnitPrice; }
    public BigDecimal getTaxRate() { return taxRate; }
    public BigDecimal getAmount() { return amount; }
    public String getRemark() { return remark; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
