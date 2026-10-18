package com.ivs.billing.invoice;

import java.math.BigDecimal;
import java.util.UUID;

/** 开票所需的销售侧只读信息（实现在 app 层）。 */
public interface BillingReferencePort {

    record OrderInfo(UUID orderId, String orderNo, UUID customerId, String customerName,
                     BigDecimal deliveredAmount) {
    }

    OrderInfo requireOrder(UUID orderId);

    String customerName(UUID customerId);
}