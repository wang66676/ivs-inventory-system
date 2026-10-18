package com.ivs.sales.order;

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

/** 销售订单：一期按全价成交、全价开票；优惠由 M4 的政策与返利承接。 */
@Entity
@Table(name = "sales_order", schema = "ivs")
public class SalesOrder {

    @Id
    private UUID id;

    @Column(name = "order_no", nullable = false, unique = true, length = 40)
    private String orderNo;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SalesOrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal totalAmount;

    /** 存在低于最低限价的明细时需要价格审批。 */
    @Column(name = "price_approval_required", nullable = false)
    private boolean priceApprovalRequired;

    @Column(name = "price_approved_by")
    private UUID priceApprovedBy;

    @Column(name = "price_approved_at")
    private Instant priceApprovedAt;

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

    protected SalesOrder() {
    }

    public static SalesOrder create(String orderNo, UUID customerId, UUID warehouseId, BigDecimal totalAmount,
                                    boolean priceApprovalRequired, String remark, UUID ownerId) {
        SalesOrder order = new SalesOrder();
        order.id = UUID.randomUUID();
        order.orderNo = orderNo;
        order.customerId = customerId;
        order.warehouseId = warehouseId;
        order.status = SalesOrderStatus.DRAFT;
        order.totalAmount = money(totalAmount);
        order.priceApprovalRequired = priceApprovalRequired;
        order.remark = remark;
        order.ownerId = ownerId;
        order.createdAt = Instant.now();
        order.updatedAt = order.createdAt;
        return order;
    }

    public void updateTotal(BigDecimal total) {
        this.totalAmount = money(total);
    }

    public void markPriceApprovalRequired(boolean required) {
        this.priceApprovalRequired = required;
    }

    /** 低价审批：仅当存在低于最低限价的明细时需要。 */
    public void approvePrice(UUID operatorId) {
        if (!priceApprovalRequired) {
            return;
        }
        this.priceApprovedBy = operatorId;
        this.priceApprovedAt = Instant.now();
    }

    public void confirm() {
        if (status == SalesOrderStatus.CONFIRMED) {
            return;
        }
        if (status != SalesOrderStatus.DRAFT) {
            throw new IllegalStateException("只有草稿订单可以确认: " + status);
        }
        if (priceApprovalRequired && priceApprovedBy == null) {
            throw new IllegalStateException("存在低于最低限价的明细，需要先完成价格审批");
        }
        this.status = SalesOrderStatus.CONFIRMED;
    }

    public void refreshDeliveryStatus(boolean fullyDelivered) {
        if (status == SalesOrderStatus.DELIVERED) {
            return;
        }
        if (status != SalesOrderStatus.CONFIRMED && status != SalesOrderStatus.PARTIALLY_DELIVERED) {
            throw new IllegalStateException("当前订单状态不允许出库: " + status);
        }
        this.status = fullyDelivered ? SalesOrderStatus.DELIVERED : SalesOrderStatus.PARTIALLY_DELIVERED;
    }

    public void close() {
        if (status == SalesOrderStatus.DELIVERED) {
            this.status = SalesOrderStatus.CLOSED;
        }
    }

    public void cancel() {
        if (status == SalesOrderStatus.CANCELLED) {
            return;
        }
        if (status == SalesOrderStatus.PARTIALLY_DELIVERED || status == SalesOrderStatus.DELIVERED) {
            throw new IllegalStateException("已有出库的订单不能取消");
        }
        this.status = SalesOrderStatus.CANCELLED;
    }

    public boolean deliverable() {
        return status == SalesOrderStatus.CONFIRMED || status == SalesOrderStatus.PARTIALLY_DELIVERED;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (status == null) {
            status = SalesOrderStatus.DRAFT;
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

    public UUID getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public UUID getCustomerId() { return customerId; }
    public UUID getWarehouseId() { return warehouseId; }
    public SalesOrderStatus getStatus() { return status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public boolean isPriceApprovalRequired() { return priceApprovalRequired; }
    public UUID getPriceApprovedBy() { return priceApprovedBy; }
    public Instant getPriceApprovedAt() { return priceApprovedAt; }
    public String getRemark() { return remark; }
    public UUID getOwnerId() { return ownerId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}