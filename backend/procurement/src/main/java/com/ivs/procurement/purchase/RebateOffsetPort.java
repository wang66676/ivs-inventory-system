package com.ivs.procurement.purchase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 返利资金池抵扣端口：入库单确认时用同一厂家的已到账返利抵扣车款。
 * 实现在 app 层（调用 policy 模块的资金池服务），采购模块不直接依赖政策模块。
 */
public interface RebateOffsetPort {

    /** @return 本次实际抵扣金额（≤ 本单金额，且 ≤ 该厂家可用返利余额） */
    BigDecimal offset(UUID supplierId, UUID purchaseOrderId, UUID purchaseReceiptId, String receiptNo,
                      BigDecimal maxAmount, List<UUID> fundTxnIds);
}