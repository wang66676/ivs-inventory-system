package com.ivs.billing.purchase;

import java.math.BigDecimal;
import java.util.UUID;

/** 返利入池端口：政策红字冲减应付后的剩余金额写入返利资金池（实现在 app 层）。 */
public interface RebateCreditPort {
    void credit(UUID supplierId, BigDecimal amount, String sourceType, String sourceNo, UUID sourceId, String remark);
}