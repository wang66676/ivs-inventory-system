package com.ivs.billing.refund;

import java.math.BigDecimal;
import java.util.UUID;

public interface RefundApprovalPort {
    Decision evaluate(BigDecimal amount);

    void syncPending(UUID refundId, String refundNo, String customerName, BigDecimal amount, Decision decision);

    boolean resolvePending(UUID refundId, boolean approved, String reason);

    void cancel(UUID refundId);

    record Decision(boolean required, String approvalPermission, String reason) {
    }
}
