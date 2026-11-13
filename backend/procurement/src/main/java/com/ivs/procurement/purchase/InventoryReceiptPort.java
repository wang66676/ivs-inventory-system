package com.ivs.procurement.purchase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 收货入库端口：采购模块声明「收货要入库」这件事，具体由库存引擎实现。
 *
 * <p>实现放在 app 层（adapter），因此采购模块不需要依赖库存模块，
 * 保持《落地方案 v1.0》第 11.2 节「业务模块之间零直接依赖」的硬约束。</p>
 */
public interface InventoryReceiptPort {

    void postReceipt(ReceiptPosting posting);

    void reverseReceipt(ReceiptPosting posting);

    void updateCertificateApplicationDate(List<String> serialNos, LocalDate certificateApplicationDate);

    void relocateSerial(String serialNo, UUID productId, UUID fromWarehouseId, UUID toWarehouseId,
                        UUID receiptId, String receiptNo);

    record ReceiptPosting(UUID receiptId, String receiptNo, UUID warehouseId, List<ReceiptPostingLine> lines) {
    }

    record ReceiptPostingLine(UUID productId, UUID warehouseId, BigDecimal quantity, BigDecimal unitPrice,
                              String batchNo, List<String> serialNos, LocalDate certificateApplicationDate) {
    }
}
