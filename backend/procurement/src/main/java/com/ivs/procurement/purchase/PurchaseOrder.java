package com.ivs.procurement.purchase;

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

/** 采购单：可跨产品大项（整车 / 电池 / 配件同一张单）。 */
@Entity
@Table(name = "purchase_order", schema = "ivs")
public class PurchaseOrder {

    @Id
    private UUID id;

    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    private String orderNo;

    /** 厂家/业务侧订单编号；系统采购单号 orderNo 仍自动生成。 */
    @Column(name = "external_order_no", length = 80)
    private String externalOrderNo;

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PurchaseOrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "expected_date")
    private LocalDate expectedDate;

    @Column(length = 300)
    private String remark;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected PurchaseOrder() {
    }

    public static PurchaseOrder create(String orderNo, String externalOrderNo, UUID supplierId, UUID warehouseId,
                                       BigDecimal totalAmount, LocalDate expectedDate, String remark, UUID ownerId) {
        PurchaseOrder order = new PurchaseOrder();
        order.id = UUID.randomUUID();
        order.orderNo = orderNo;
        order.externalOrderNo = normalize(externalOrderNo);
        order.supplierId = supplierId;
        order.warehouseId = warehouseId;
        order.status = PurchaseOrderStatus.DRAFT;
        order.totalAmount = money(totalAmount);
        order.expectedDate = expectedDate;
        order.remark = remark;
        order.ownerId = ownerId;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        return order;
    }

    public void approve() {
        if (status == PurchaseOrderStatus.APPROVED) {
            return;
        }
        if (status != PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateException("只有草稿采购单可以审核: " + status);
        }
        this.status = PurchaseOrderStatus.APPROVED;
    }

    /** 收货后推进状态：全部收满 → RECEIVED，否则 PARTIALLY_RECEIVED。 */
    public void refreshReceiveStatus(boolean fullyReceived) {
        if (status == PurchaseOrderStatus.RECEIVED) {
            return;
        }
        if (status != PurchaseOrderStatus.APPROVED && status != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("当前采购单状态不允许收货: " + status);
        }
        this.status = fullyReceived ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    /** 收货退回后恢复可收货状态；若仍有其它有效收货则保持部分收货。 */
    public void afterReceiptReturn(boolean anyReceived) {
        if (status != PurchaseOrderStatus.PARTIALLY_RECEIVED && status != PurchaseOrderStatus.RECEIVED) {
            throw new IllegalStateException("当前采购单状态不允许收货退回: " + status);
        }
        this.status = anyReceived ? PurchaseOrderStatus.PARTIALLY_RECEIVED : PurchaseOrderStatus.APPROVED;
    }

    public void cancel() {
        if (status == PurchaseOrderStatus.CANCELLED) {
            return;
        }
        if (status == PurchaseOrderStatus.PARTIALLY_RECEIVED || status == PurchaseOrderStatus.RECEIVED) {
            throw new IllegalStateException("已有收货的采购单不能取消");
        }
        this.status = PurchaseOrderStatus.CANCELLED;
    }

    public boolean receivable() {
        return status == PurchaseOrderStatus.APPROVED || status == PurchaseOrderStatus.PARTIALLY_RECEIVED;
    }

    public void updateTotal(BigDecimal total) {
        this.totalAmount = money(total);
    }

    /** 草稿补全：订单编号、供应商、预计到货、备注与初始仓库均可后续调整。 */
    public void updateDraft(String externalOrderNo, UUID supplierId, UUID warehouseId,
                            LocalDate expectedDate, String remark) {
        if (status != PurchaseOrderStatus.DRAFT) {
            throw new IllegalStateException("只有草稿采购单可以编辑: " + status);
        }
        this.externalOrderNo = normalize(externalOrderNo);
        this.supplierId = supplierId;
        this.warehouseId = warehouseId;
        this.expectedDate = expectedDate;
        this.remark = remark;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = PurchaseOrderStatus.DRAFT;
        }
        if (totalAmount == null) {
            totalAmount = money(BigDecimal.ZERO);
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

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public UUID getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public String getExternalOrderNo() { return externalOrderNo; }
    public UUID getSupplierId() { return supplierId; }
    public UUID getWarehouseId() { return warehouseId; }
    public PurchaseOrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public LocalDate getExpectedDate() { return expectedDate; }
    public String getRemark() { return remark; }
    public UUID getOwnerId() { return ownerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
