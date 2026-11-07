package com.ivs.billing.refund.api;

import com.ivs.billing.refund.RefundOrder;
import com.ivs.billing.refund.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class RefundApi {
    private RefundApi() {
    }

    public record RefundView(UUID id, String refundNo, UUID customerId, String customerName,
                             BigDecimal amount, String method, String sourceType, String sourceNo,
                             String reason, RefundStatus status, String failureReason,
                             String executionRemark, int retryCount, boolean manualResolved,
                             UUID requestedBy, UUID approvedBy, Instant approvedAt, Instant executedAt,
                             Instant createdAt, Instant updatedAt) {
        public static RefundView from(RefundOrder order, String customerName) {
            return new RefundView(order.getId(), order.getRefundNo(), order.getCustomerId(), customerName,
                    order.getAmount(), order.getMethod(), order.getSourceType(), order.getSourceNo(),
                    order.getReason(), order.getStatus(), order.getFailureReason(), order.getExecutionRemark(),
                    order.getRetryCount(), order.isManualResolved(), order.getRequestedBy(), order.getApprovedBy(),
                    order.getApprovedAt(), order.getExecutedAt(), order.getCreatedAt(), order.getUpdatedAt());
        }
    }

    public record CreateRefundCommand(UUID customerId, BigDecimal amount, String method,
                                      String sourceType, String sourceNo, String reason) {
    }

    public record ExecuteRefundCommand(boolean success, String failureReason, String remark) {
    }

    public record ManualCompleteCommand(String remark) {
    }
}
