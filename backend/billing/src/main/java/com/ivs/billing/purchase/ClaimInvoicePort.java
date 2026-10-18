package com.ivs.billing.purchase;

import java.math.BigDecimal;
import java.util.UUID;

/** 申报单结算端口：由 app 层适配政策模块，billing 不直接依赖 policy。 */
public interface ClaimInvoicePort {

    ClaimInvoiceInfo validateForInvoice(UUID claimId, UUID supplierId, BigDecimal amount);

    void settleFromInvoice(UUID claimId, UUID invoiceId, String invoiceNo);

    record ClaimInvoiceInfo(UUID claimId, String claimNo, UUID supplierId, BigDecimal confirmedAmount) {
    }
}
