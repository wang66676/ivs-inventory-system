package com.ivs.inventory.stocktake;

import java.math.BigDecimal;
import java.util.UUID;

/** 盘点过账审批端口：实现位于 app 层。 */
public interface StocktakeApprovalPort {
    AdjustmentDecision evaluate(BigDecimal differenceAmount);

    void syncPending(UUID stocktakeId, String stocktakeNo, BigDecimal differenceAmount, AdjustmentDecision decision);

    void cancel(UUID stocktakeId);

    record AdjustmentDecision(boolean required, String approvalPermission, String reason) {
        public static AdjustmentDecision none() {
            return new AdjustmentDecision(false, null, null);
        }
    }
}
