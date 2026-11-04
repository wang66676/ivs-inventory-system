package com.ivs.sales.order;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** 主数据与取价只读端口（实现放在 app 层，保持模块之间零直接依赖）。 */
public interface SalesMasterDataPort {

    record ProductInfo(UUID id, String productNo, String name, String categoryCode, String categoryName,
                       String trackingMode) {
    }

    record CustomerInfo(UUID id, String code, String name, String customerType, BigDecimal creditLimit,
                        int paymentTermDays) {
    }

    record WarehouseInfo(UUID id, String name) {
    }

    /** 取价结果：source 为 PRICE_LIST:xxx 或 PRODUCT_GUIDE。 */
    record PriceInfo(BigDecimal price, BigDecimal minPrice, String source) {
    }

    ProductInfo requireProduct(UUID productId);

    CustomerInfo requireCustomer(UUID customerId);

    WarehouseInfo requireWarehouse(UUID warehouseId);

    PriceInfo lookupPrice(UUID productId, String customerType, LocalDate date);
}
