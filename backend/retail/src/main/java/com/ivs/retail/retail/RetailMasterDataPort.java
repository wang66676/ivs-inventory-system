package com.ivs.retail.retail;

import java.util.UUID;

/** 实销所需的主数据只读端口（实现放 app 层）。 */
public interface RetailMasterDataPort {

    record SerialInfo(UUID id, String serialNo, UUID productId, String productNo, String productName,
                      String categoryCode, String trackingMode, String serialStatus) {
    }

    SerialInfo requireSerial(UUID serialId);

    String customerName(UUID customerId);
}