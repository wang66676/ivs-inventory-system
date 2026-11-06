package com.ivs.app;

import com.ivs.inventory.stock.api.InventoryApi;
import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.procurement.purchase.api.ProcurementApi;
import com.ivs.retail.api.RetailApi;
import com.ivs.sales.order.api.SalesApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 库龄分析与交付待实销预警：入库可见、交付预警、登记实销后闭环。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RetailPendingAndAgeIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 车辆交付后进入未实销预警且登记实销后消失并可查询库龄() {
        MasterdataApi.CategoryView vehicle = post("/api/ivs/product-categories", Map.of(
                "code", "VEHICLE-PA", "name", "整车", "trackingMode", TrackingMode.SERIAL.name(),
                "statusTemplate", "VEHICLE", "unit", "台", "sortOrder", 0), MasterdataApi.CategoryView.class);
        Map<String, Object> productBody = new HashMap<>();
        productBody.put("name", "Q3 领航版");
        productBody.put("categoryId", vehicle.id().toString());
        productBody.put("unit", "台");
        productBody.put("series", "Q3");
        productBody.put("model", "T31");
        productBody.put("minPrice", 0);
        productBody.put("salePriceGuide", 450000);
        MasterdataApi.ProductView product = post("/api/ivs/products", productBody, MasterdataApi.ProductView.class);
        MasterdataApi.WarehouseView warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "PA-WH", "name", "整车仓", "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
        MasterdataApi.SupplierView supplier = post("/api/ivs/suppliers", Map.of(
                "name", "预警整车厂", "supplierType", SupplierType.OEM.name()), MasterdataApi.SupplierView.class);
        MasterdataApi.CustomerView customer = post("/api/ivs/customers", Map.of(
                "name", "预警客户", "customerType", CustomerType.COMPANY.name(), "creditLimit", 5000000),
                MasterdataApi.CustomerView.class);
        MasterdataApi.SerialView serial = post("/api/ivs/product-serials", Map.of(
                "serialNo", "LZZPA2026000001", "productId", product.id().toString()), MasterdataApi.SerialView.class);

        ProcurementApi.PurchaseOrderView purchase = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.id().toString(), "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1,
                        "unitPrice", 400000))), ProcurementApi.PurchaseOrderView.class);
        purchase = post("/api/ivs/purchase-orders/" + purchase.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        post("/api/ivs/purchase-orders/" + purchase.id() + "/receive", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", purchase.lines().get(0).id().toString(),
                        "serialNos", List.of("LZZPA2026000001")))), ProcurementApi.PurchaseReceiptView.class);

        InventoryApi.AgeAnalysisView[] ageRows = restTemplate.getForEntity(
                "/api/ivs/inventory/age-analysis", InventoryApi.AgeAnalysisView[].class).getBody();
        assertThat(ageRows).isNotNull().singleElement().satisfies(row -> {
            assertThat(row.serialNo()).isEqualTo("LZZPA2026000001");
            assertThat(row.series()).isEqualTo("Q3");
            assertThat(row.warehouseName()).isEqualTo("整车仓");
            assertThat(row.ageDays()).isGreaterThanOrEqualTo(0);
            assertThat(row.ageBucket()).isNotBlank();
            assertThat(row.purchasePrice()).isEqualByComparingTo("400000.00");
        });

        SalesApi.SalesOrderView sales = post("/api/ivs/sales-orders", Map.of(
                "customerId", customer.id().toString(), "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1,
                        "unitPrice", 450000, "serialIds", List.of(serial.id().toString())))),
                SalesApi.SalesOrderView.class);
        sales = post("/api/ivs/sales-orders/" + sales.id() + "/confirm", Map.of(), SalesApi.SalesOrderView.class);
        sales = post("/api/ivs/sales-orders/" + sales.id() + "/lock", Map.of(), SalesApi.SalesOrderView.class);
        post("/api/ivs/sales-orders/" + sales.id() + "/deliver", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", sales.lines().get(0).id().toString(),
                        "serialIds", List.of(serial.id().toString())))), SalesApi.SalesDeliveryView.class);

        String orderNo = sales.externalOrderNo();
        Map<?, ?>[] pending = restTemplate.getForEntity("/api/ivs/retail-pending", Map[].class).getBody();
        assertThat(pending).isNotNull().singleElement().satisfies(row -> {
            assertThat(((Map<?, ?>) row.get("serial")).get("serialNo")).isEqualTo("LZZPA2026000001");
            assertThat(row.get("orderNo")).isEqualTo(orderNo);
            assertThat(row.get("series")).isEqualTo("Q3");
            assertThat(((Number) row.get("pendingDays")).longValue()).isGreaterThanOrEqualTo(0);
        });

        post("/api/ivs/retails", Map.of(
                "retailDate", LocalDate.now().toString(),
                "invoiceIssuedAt", Instant.now().minus(1, ChronoUnit.DAYS).toString(),
                "serialId", serial.id().toString(), "userName", "预警客户使用人",
                "userMobile", "13800000000", "salePrice", 450000), RetailApi.RetailView.class);

        Map<?, ?>[] afterRetail = restTemplate.getForEntity("/api/ivs/retail-pending", Map[].class).getBody();
        assertThat(afterRetail).isNotNull().isEmpty();
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}
