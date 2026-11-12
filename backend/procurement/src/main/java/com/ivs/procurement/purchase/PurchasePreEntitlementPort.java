package com.ivs.procurement.purchase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 采购单返利政策同步预享受项的端口。
 *
 * <p>采购模块只声明同步需求，具体适配器放在 app 层，避免业务模块直接依赖政策模块。</p>
 */
public interface PurchasePreEntitlementPort {

    void clear(UUID purchaseOrderId);

    void sync(UUID purchaseOrderId, List<PreEntitlementLine> lines);

    record PreEntitlementLine(UUID orderLineId, UUID productId, BigDecimal unitPrice, List<UUID> policyIds) {
    }

    void syncSerials(UUID purchaseOrderId, UUID orderLineId, UUID productId, BigDecimal unitPrice,
                     List<UUID> policyIds, List<String> serialNos);
}
