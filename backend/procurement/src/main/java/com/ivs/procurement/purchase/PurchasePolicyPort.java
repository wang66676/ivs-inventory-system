package com.ivs.procurement.purchase;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * 采购模块读取返利政策的只读端口。
 * 实现放在 app 层，采购模块只保存政策 ID，不直接依赖政策模块。
 */
public interface PurchasePolicyPort {

    record PolicyInfo(UUID id, String policyNo, String name, UUID supplierId, String status) {
    }

    List<PolicyInfo> findByIds(Collection<UUID> policyIds);
}
