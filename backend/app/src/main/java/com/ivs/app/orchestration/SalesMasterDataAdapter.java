package com.ivs.app.orchestration;

import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.partner.Customer;
import com.ivs.masterdata.partner.CustomerService;

import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.warehouse.WarehouseService;
import com.ivs.sales.order.SalesMasterDataPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** 销售模块的主数据与取价端口实现（app 层适配，保持模块零直接依赖）。 */
@Component
public class SalesMasterDataAdapter implements SalesMasterDataPort {

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
                product.trackingMode().name());
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
    public PriceInfo lookupPrice(UUID productId, String customerType, LocalDate date) {
        MasterdataApi.ProductView product = productService.get(productId);
        return new PriceInfo(product.salePriceGuide() == null ? BigDecimal.ZERO : product.salePriceGuide(),
                product.minPrice(), "PRODUCT_GUIDE");
    }
}

