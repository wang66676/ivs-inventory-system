package com.ivs.lease;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** 租赁模块使用的主数据与资产操作端口，具体实现位于 app 层。 */
public interface LeaseMasterDataPort {
    record SerialInfo(UUID id, UUID productId, String serialNo, String productName,
                      String categoryCode, String categoryName, String status, UUID warehouseId,
                      String batchNo, BigDecimal purchasePrice) {
    }

    record CustomerInfo(UUID id, String name) {
    }

    SerialInfo requireSerial(UUID serialId);
    CustomerInfo requireCustomer(UUID customerId);
    void activateAsset(SerialInfo serial, UUID contractId, String contractNo, LocalDate startDate);
    void endLeaseAsset(SerialInfo serial, UUID contractId, String contractNo);
}
