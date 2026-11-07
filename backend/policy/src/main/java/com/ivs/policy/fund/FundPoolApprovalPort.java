package com.ivs.policy.fund;

import java.math.BigDecimal;
import java.util.UUID;

/** 返利资金池人工调整审批端口。 */
public interface FundPoolApprovalPort {
    AdjustmentDecision evaluate(BigDecimal amount);

    void syncPending(UUID requestId, String businessNo, BigDecimal amount,
                     AdjustmentDecision decision, String payloadJson);

    record AdjustmentDecision(boolean required, String approvalPermission, String reason) {
    }

    record CreditPayload(UUID supplierId, BigDecimal amount, String sourceType, String sourceNo,
                         UUID sourceId, String remark) {
    }
}
