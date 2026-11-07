package com.ivs.sales.order;

import java.math.BigDecimal;
import java.util.UUID;

/** 销售审批判定端口：实现放在 app 层，避免销售模块依赖平台审批实体。 */
public interface SalesApprovalPort {
    LowPriceDecision evaluateLowPrice(BigDecimal salePrice, BigDecimal minPrice);

    void syncLowPriceApproval(UUID orderId, String orderNo, BigDecimal totalAmount, LowPriceDecision decision);

    boolean resolveLowPriceApproval(UUID orderId, boolean approved, String reason);

    void cancelLowPriceApproval(UUID orderId);

    record LowPriceDecision(boolean required, String approvalPermission, String reason) {
        public static LowPriceDecision none() {
            return new LowPriceDecision(false, null, null);
        }
    }
}
