package com.ivs.policy.reference;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** 政策/申报所需的跨模块只读信息，由 app 层适配到主数据与实销模块。 */
public interface PolicyReferencePort {

    String supplierName(UUID supplierId);

    RetailInfo retail(UUID retailId);

    record RetailInfo(String retailNo, String serialNo, String productNo, String productName,
                      BigDecimal salePrice, LocalDate retailDate, UUID storeId) {
    }
}
