package com.ivs.billing.purchase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 政策红字发票按 VIN/SN 冲减对应采购订单应付的跨模块端口。 */
public interface PayableCreditPort {

    CreditResult apply(UUID supplierId, List<RebateLine> lines, UUID claimId, String invoiceNo);

    record RebateLine(String serialNo, UUID purchaseOrderId, UUID purchaseReceiptId,
                      BigDecimal amount) {
    }

    record CreditResult(BigDecimal appliedAmount, BigDecimal unappliedAmount) {
    }
}
