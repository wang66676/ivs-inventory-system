package com.ivs.billing.refund;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/** 独立客户退款单：申请审批通过后执行，失败可重试或人工兜底。 */
@Entity
@Table(name = "refund_order", schema = "ivs")
public class RefundOrder {
    @Id
    private UUID id;
    @Column(name = "refund_no", nullable = false, unique = true, length = 40)
    private String refundNo;
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 30)
    private String method;
    @Column(name = "source_type", nullable = false, length = 40)
    private String sourceType;
    @Column(name = "source_no", length = 100)
    private String sourceNo;
    @Column(nullable = false, length = 500)
    private String reason;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RefundStatus status;
    @Column(name = "failure_reason", length = 500)
    private String failureReason;
    @Column(name = "execution_remark", length = 500)
    private String executionRemark;
    @Column(name = "retry_count", nullable = false)
    private int retryCount;
    @Column(name = "manual_resolved", nullable = false)
    private boolean manualResolved;
    @Column(name = "requested_by")
    private UUID requestedBy;
    @Column(name = "approved_by")
    private UUID approvedBy;
    @Column(name = "approved_at")
    private Instant approvedAt;
    @Column(name = "executed_at")
    private Instant executedAt;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    @Column(nullable = false)
    private long version;

    protected RefundOrder() {
    }

    public static RefundOrder create(String refundNo, UUID customerId, BigDecimal amount, String method,
                                     String sourceType, String sourceNo, String reason, UUID requestedBy) {
        RefundOrder refund = new RefundOrder();
        refund.id = UUID.randomUUID();
        refund.refundNo = refundNo;
        refund.customerId = customerId;
        refund.amount = money(amount);
        if (refund.amount.signum() <= 0) throw new IllegalArgumentException("退款金额必须大于 0");
        refund.method = method == null || method.isBlank() ? "BANK_TRANSFER" : method.trim();
        refund.sourceType = sourceType == null || sourceType.isBlank() ? "MANUAL" : sourceType.trim();
        refund.sourceNo = trimToNull(sourceNo);
        refund.reason = reason == null ? "" : reason.trim();
        if (refund.reason.isBlank()) throw new IllegalArgumentException("退款原因不能为空");
        refund.status = RefundStatus.PENDING_APPROVAL;
        refund.retryCount = 0;
        refund.requestedBy = requestedBy;
        refund.createdAt = Instant.now();
        refund.updatedAt = refund.createdAt;
        return refund;
    }

    public void approve(UUID actorId) {
        if (status != RefundStatus.PENDING_APPROVAL) throw new IllegalStateException("只有待审批退款可以审批通过");
        status = RefundStatus.APPROVED;
        approvedBy = actorId;
        approvedAt = Instant.now();
        updatedAt = approvedAt;
    }

    public void reject() {
        if (status != RefundStatus.PENDING_APPROVAL) throw new IllegalStateException("只有待审批退款可以驳回");
        status = RefundStatus.REJECTED;
        updatedAt = Instant.now();
    }

    public void execute(boolean success, String failureReason, String executionRemark) {
        if (status != RefundStatus.APPROVED && status != RefundStatus.FAILED) {
            throw new IllegalStateException("只有已审批或失败退款可以执行");
        }
        if (status == RefundStatus.FAILED) retryCount++;
        this.executionRemark = trimToNull(executionRemark);
        this.executedAt = Instant.now();
        if (success) {
            status = RefundStatus.SUCCESS;
            this.failureReason = null;
            manualResolved = false;
        } else {
            String error = trimToNull(failureReason);
            if (error == null) throw new IllegalArgumentException("执行失败必须填写失败原因");
            status = RefundStatus.FAILED;
            this.failureReason = error;
        }
        updatedAt = executedAt;
    }

    public void manualComplete(String remark) {
        if (status != RefundStatus.FAILED && status != RefundStatus.APPROVED) {
            throw new IllegalStateException("当前退款状态不能人工确认完成");
        }
        status = RefundStatus.SUCCESS;
        failureReason = null;
        executionRemark = remark == null || remark.isBlank() ? "人工确认退款完成" : remark.trim();
        manualResolved = true;
        executedAt = Instant.now();
        updatedAt = executedAt;
    }

    public void cancel() {
        if (status != RefundStatus.PENDING_APPROVAL && status != RefundStatus.APPROVED) {
            throw new IllegalStateException("当前退款状态不能取消");
        }
        status = RefundStatus.CANCELLED;
        updatedAt = Instant.now();
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getRefundNo() { return refundNo; }
    public UUID getCustomerId() { return customerId; }
    public BigDecimal getAmount() { return amount; }
    public String getMethod() { return method; }
    public String getSourceType() { return sourceType; }
    public String getSourceNo() { return sourceNo; }
    public String getReason() { return reason; }
    public RefundStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public String getExecutionRemark() { return executionRemark; }
    public int getRetryCount() { return retryCount; }
    public boolean isManualResolved() { return manualResolved; }
    public UUID getRequestedBy() { return requestedBy; }
    public UUID getApprovedBy() { return approvedBy; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getExecutedAt() { return executedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
