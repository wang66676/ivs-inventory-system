package com.ivs.sales.returns;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 销售退货入库端口：由 app 层适配库存与序列号模块。 */
public interface SalesReturnStockPort {
    void postReturn(Posting posting);

    record Posting(UUID returnId, String returnNo, UUID warehouseId, List<PostingLine> lines) {
    }

    record PostingLine(UUID productId, BigDecimal quantity, BigDecimal unitCost,
                       String batchNo, List<String> serialNos) {
    }
}
