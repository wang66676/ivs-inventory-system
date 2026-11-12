package com.ivs.policy.reference;

import java.time.LocalDate;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 预享受项展示所需的订单编号与实销时间，由 app 层跨模块装配。 */
public interface PreEntitlementReferencePort {

    record OrderReference(String serialNos, String orderNo, LocalDate orderDate, Instant invoiceIssuedAt,
                          LocalDate retailDate, LocalDate deliveryDate, LocalDate registerDate) {
    }

    /** 采购收货明细对应的单车信息，用于修复历史行级预享受项并补齐到 VIN 级。 */
    record ReceivedSerial(UUID orderLineId, UUID productId, BigDecimal unitPrice, UUID serialId) {
    }

    OrderReference describe(UUID orderId, UUID orderLineId, UUID serialId);

    List<ReceivedSerial> receivedSerials(UUID orderId);
}
