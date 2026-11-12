package com.ivs.app.orchestration;

import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.partner.Customer;
import com.ivs.masterdata.partner.CustomerService;

import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.warehouse.WarehouseService;
import com.ivs.masterdata.WarehouseType;
import com.ivs.sales.order.SalesMasterDataPort;
import com.ivs.shared.error.ConflictException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** 销售模块的主数据与取价端口实现（app 层适配，保持模块零直接依赖）。 */
@Component
public class SalesMasterDataAdapter implements SalesMasterDataPort {

    private static final String BATTERY_WAREHOUSE_CODE = "BATTERY-TJRD";
    private static final String BATTERY_WAREHOUSE_NAME = "天津容大";

    /** 客户账期暂用默认值；后续可在客户档案增加账期字段。 */
    private static final int DEFAULT_PAYMENT_TERM_DAYS = 30;

    private final ProductService productService;
    private final CustomerService customerService;
    private final WarehouseService warehouseService;


    public SalesMasterDataAdapter(ProductService productService,
                                  CustomerService customerService,
                                  WarehouseService warehouseService) {
        this.productService = productService;
        this.customerService = customerService;
        this.warehouseService = warehouseService;

    }

    @Override
    public ProductInfo requireProduct(UUID productId) {
        MasterdataApi.ProductView product = productService.get(productId);
        return new ProductInfo(product.id(), product.productNo(), product.name(), product.categoryCode(),
                product.categoryName(), product.trackingMode().name());
    }

    @Override
    public CustomerInfo requireCustomer(UUID customerId) {
        Customer customer = customerService.find(customerId);
        return new CustomerInfo(customer.getId(), customer.getCode(), customer.getName(),
                customer.getCustomerType().name(), customer.getCreditLimit(), DEFAULT_PAYMENT_TERM_DAYS);
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
                        "动力电池固定发货仓", null));
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

    @Override
    public PriceInfo lookupPrice(UUID productId, String customerType, LocalDate date) {
        MasterdataApi.ProductView product = productService.get(productId);
        return new PriceInfo(product.salePriceGuide() == null ? BigDecimal.ZERO : product.salePriceGuide(),
                product.minPrice(), "PRODUCT_GUIDE");
    }
}

