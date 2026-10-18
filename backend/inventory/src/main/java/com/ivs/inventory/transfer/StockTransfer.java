package com.ivs.inventory.transfer;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 库存调拨单头：草稿 → 已审核 → 在途 → 已签收。 */
@Entity
@Table(name = "stock_transfer", schema = "ivs")
public class StockTransfer {

    @Id private UUID id;
    @Column(name = "transfer_no", nullable = false, unique = true, length = 40) private String transferNo;
    @Column(name = "from_warehouse_id", nullable = false) private UUID fromWarehouseId;
    @Column(name = "to_warehouse_id", nullable = false) private UUID toWarehouseId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StockTransferStatus status;
    @Column(name = "line_count", nullable = false) private int lineCount;
    @Column(name = "total_quantity", nullable = false, precision = 18, scale = 3) private BigDecimal totalQuantity;
    @Column(name = "received_quantity", nullable = false, precision = 18, scale = 3) private BigDecimal receivedQuantity;
    @Column(name = "difference_quantity", nullable = false, precision = 18, scale = 3) private BigDecimal differenceQuantity;
    @Column(length = 300) private String remark;
    @Column(name = "approved_by") private UUID approvedBy;
    @Column(name = "approved_at") private Instant approvedAt;
    @Column(name = "dispatched_by") private UUID dispatchedBy;
    @Column(name = "dispatched_at") private Instant dispatchedAt;
    @Column(name = "received_by") private UUID receivedBy;
    @Column(name = "received_at") private Instant receivedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private long version;

    protected StockTransfer() {
    }

    public static StockTransfer create(String transferNo, UUID fromWarehouseId, UUID toWarehouseId,
                                       UUID createdBy, String remark) {
        StockTransfer transfer = new StockTransfer();
        transfer.id = UUID.randomUUID();
        transfer.transferNo = transferNo;
        transfer.fromWarehouseId = fromWarehouseId;
        transfer.toWarehouseId = toWarehouseId;
        transfer.status = StockTransferStatus.DRAFT;
        transfer.totalQuantity = qty(BigDecimal.ZERO);
        transfer.receivedQuantity = qty(BigDecimal.ZERO);
        transfer.differenceQuantity = qty(BigDecimal.ZERO);
        transfer.remark = remark;
        transfer.createdBy = createdBy;
        transfer.createdAt = Instant.now();
        transfer.updatedAt = transfer.createdAt;
        return transfer;
    }

    public void summarize(List<StockTransferLine> lines) {
        requireStatus(StockTransferStatus.DRAFT, "只有草稿调拨单可以修改");
        this.lineCount = lines.size();
        this.totalQuantity = qty(lines.stream().map(StockTransferLine::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    public void approve(UUID operatorId) {
        requireStatus(StockTransferStatus.DRAFT, "只有草稿调拨单可以审核");
        if (totalQuantity.signum() <= 0) throw new IllegalStateException("调拨数量必须大于 0");
        this.status = StockTransferStatus.APPROVED;
        this.approvedBy = operatorId;
        this.approvedAt = Instant.now();
    }

    public void dispatch(UUID operatorId) {
        requireStatus(StockTransferStatus.APPROVED, "只有已审核调拨单可以发出");
        this.status = StockTransferStatus.IN_TRANSIT;
        this.dispatchedBy = operatorId;
        this.dispatchedAt = Instant.now();
    }

    public void receive(UUID operatorId, List<StockTransferLine> lines) {
        requireStatus(StockTransferStatus.IN_TRANSIT, "只有在途调拨单可以签收");
        this.receivedQuantity = qty(lines.stream().map(StockTransferLine::getReceivedQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        this.differenceQuantity = qty(totalQuantity.subtract(receivedQuantity));
        this.status = StockTransferStatus.RECEIVED;
        this.receivedBy = operatorId;
        this.receivedAt = Instant.now();
    }

    public void cancel() {
        if (status == StockTransferStatus.IN_TRANSIT) {
            throw new IllegalStateException("在途调拨不能取消，请通过签收差异处理");
        }
        if (status == StockTransferStatus.RECEIVED || status == StockTransferStatus.CANCELLED) {
            throw new IllegalStateException("已签收或已取消的调拨单不能取消");
        }
        this.status = StockTransferStatus.CANCELLED;
        this.cancelledAt = Instant.now();
    }

    private void requireStatus(StockTransferStatus expected, String message) {
        if (status != expected) throw new IllegalStateException(message);
    }

    @PrePersist void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (status == null) status = StockTransferStatus.DRAFT;
        if (totalQuantity == null) totalQuantity = qty(BigDecimal.ZERO);
        if (receivedQuantity == null) receivedQuantity = qty(BigDecimal.ZERO);
        if (differenceQuantity == null) differenceQuantity = qty(BigDecimal.ZERO);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }
    @PreUpdate void preUpdate() { updatedAt = Instant.now(); }

    private static BigDecimal qty(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    public UUID getId() { return id; }
    public String getTransferNo() { return transferNo; }
    public UUID getFromWarehouseId() { return fromWarehouseId; }
    public UUID getToWarehouseId() { return toWarehouseId; }
    public StockTransferStatus getStatus() { return status; }
    public int getLineCount() { return lineCount; }
    public BigDecimal getTotalQuantity() { return totalQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public BigDecimal getDifferenceQuantity() { return differenceQuantity; }
    public String getRemark() { return remark; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public UUID getDispatchedBy() { return dispatchedBy; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public UUID getReceivedBy() { return receivedBy; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
