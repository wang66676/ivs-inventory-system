package com.ivs.app.orchestration;

import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.partner.SupplierService;
import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.warehouse.WarehouseService;
import com.ivs.masterdata.WarehouseType;
import com.ivs.procurement.purchase.MasterDataQueryPort;
import com.ivs.shared.error.ConflictException;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 主数据只读端口的实现：供采购等模块读取商品 / 供应商 / 仓库（模块之间零直接依赖）。 */
@Component
public class MasterDataQueryAdapter implements MasterDataQueryPort {

    private static final String BATTERY_WAREHOUSE_CODE = "BATTERY-TJRD";
    private static final String BATTERY_WAREHOUSE_NAME = "天津容大";

    private final ProductService productService;
    private final SupplierService supplierService;
    private final WarehouseService warehouseService;

    public MasterDataQueryAdapter(ProductService productService,
                                  SupplierService supplierService,
                                  WarehouseService warehouseService) {
        this.productService = productService;
        this.supplierService = supplierService;
        this.warehouseService = warehouseService;
    }

    @Override
    public ProductInfo requireProduct(UUID productId) {
        MasterdataApi.ProductView product = productService.get(productId);
        return new ProductInfo(product.id(), product.productNo(), product.name(), product.series(),
                product.configCode(), product.categoryCode(), product.categoryName(), product.trackingMode().name());
    }

    @Override
    public SupplierInfo requireSupplier(UUID supplierId) {
        MasterdataApi.SupplierView supplier = supplierService.get(supplierId);
        return new SupplierInfo(supplier.id(), supplier.name(), supplier.supplierType().name());
    }

    @Override
    public WarehouseInfo requireWarehouse(UUID warehouseId) {
        MasterdataApi.WarehouseView warehouse = warehouseService.get(warehouseId);
        return new WarehouseInfo(warehouse.id(), warehouse.name());
    }

    @Override
    public WarehouseInfo requireBatteryWarehouse() {
        var existing = warehouseService.list().stream()
                .filter(warehouse -> BATTERY_WAREHOUSE_NAME.equals(warehouse.name())
                        || BATTERY_WAREHOUSE_CODE.equalsIgnoreCase(warehouse.code()))
                .findFirst();
        MasterdataApi.WarehouseView warehouse = existing.orElseGet(() -> {
            try {
                return warehouseService.create(new MasterdataApi.SaveWarehouseCommand(
                        BATTERY_WAREHOUSE_CODE, BATTERY_WAREHOUSE_NAME, WarehouseType.MAIN,
                        "动力电池固定收货仓", null));
            } catch (ConflictException exception) {
                return warehouseService.list().stream()
                        .filter(item -> BATTERY_WAREHOUSE_NAME.equals(item.name())
                                || BATTERY_WAREHOUSE_CODE.equalsIgnoreCase(item.code()))
                        .findFirst()
                        .orElseThrow(() -> exception);
            }
        });
        return new WarehouseInfo(warehouse.id(), warehouse.name());
    }
}


