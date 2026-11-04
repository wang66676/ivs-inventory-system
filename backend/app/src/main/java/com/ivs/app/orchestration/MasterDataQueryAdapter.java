package com.ivs.app.orchestration;

import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.partner.SupplierService;
import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.warehouse.WarehouseService;
import com.ivs.procurement.purchase.MasterDataQueryPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 主数据只读端口的实现：供采购等模块读取商品 / 供应商 / 仓库（模块之间零直接依赖）。 */
@Component
public class MasterDataQueryAdapter implements MasterDataQueryPort {

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
}


