package com.ivs.procurement.returns;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 采购退货出库端口：采购模块声明退货要扣库存，具体由 app 层适配库存模块。 */
public interface PurchaseReturnStockPort {
    void postReturn(Posting posting);

    record Posting(UUID returnId, String returnNo, UUID warehouseId, List<PostingLine> lines) {
    }

    record PostingLine(UUID productId, BigDecimal quantity, String batchNo, List<String> serialNos) {
    }
}
