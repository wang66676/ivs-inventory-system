package com.ivs.procurement.purchase;

import java.util.UUID;

/**
 * 主数据只读端口：采购需要校验并展示商品 / 供应商 / 仓库名称，但不应直接依赖主数据模块。
 * 实现放在 app 层。
 */
public interface MasterDataQueryPort {

    record ProductInfo(UUID id, String productNo, String name, String series, String configCode,
                       String categoryCode, String categoryName, String trackingMode) {
    }

    record SupplierInfo(UUID id, String name, String supplierType) {
    }

    record WarehouseInfo(UUID id, String name) {
    }

    ProductInfo requireProduct(UUID productId);

    SupplierInfo requireSupplier(UUID supplierId);

    WarehouseInfo requireWarehouse(UUID warehouseId);

    /** 动力电池统一收货仓库：天津容大。 */
    WarehouseInfo requireBatteryWarehouse();
}

