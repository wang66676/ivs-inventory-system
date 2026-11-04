package com.ivs.app;

import com.ivs.inventory.stock.api.InventoryApi;
import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.procurement.purchase.api.ProcurementApi;
import com.ivs.sales.order.SalesOrderStatus;
import com.ivs.sales.order.api.SalesApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 动力电池采购、收货、销售独立匹配，以及无返利政策/无实销的验收。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BatteryProcurementSalesIntegrationTest {

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
    void 电池按箱包分项采购销售且不进入返利与实销() {
        MasterdataApi.CategoryView batteryCategory = createCategory(
                "BATTERY", "动力电池", TrackingMode.SERIAL, "BATTERY", "组");
        MasterdataApi.ProductView battery = createProduct("282kWh 动力电池", batteryCategory.id());
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-BAT", "电池仓");
        MasterdataApi.SupplierView supplier = createSupplier("动力电池厂", SupplierType.OEM);
        MasterdataApi.CustomerView customer = createCustomer("电池客户", CustomerType.COMPANY);

        Map<String, Object> batteryLine = new HashMap<>();
        batteryLine.put("productId", battery.id().toString());
        batteryLine.put("quantity", 1);
        batteryLine.put("unitPrice", 300000);
        batteryLine.put("batteryBoxManufacturer", "电池箱厂 A");
        batteryLine.put("batteryBoxModel", "BOX-282");
        batteryLine.put("batteryBoxUnitPrice", 100000);
        batteryLine.put("batteryPackManufacturer", "电池包厂 B");
        batteryLine.put("batteryEnergyKwh", new BigDecimal("282.000"));
        batteryLine.put("batteryPackUnitPrice", 200000);

        ResponseEntity<Map> policyRejected = restTemplate.postForEntity("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.id().toString(),
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(withPolicy(batteryLine, List.of(UUID.randomUUID())))
        ), Map.class);
        assertThat(policyRejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(policyRejected.getBody()).isNotNull();
        assertThat(policyRejected.getBody().get("code")).isEqualTo("BATTERY_POLICY_NOT_ALLOWED");

        ProcurementApi.PurchaseOrderView purchase = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.id().toString(),
                "warehouseId", warehouse.id().toString(),
                "externalOrderNo", "BAT-PO-001",
                "lines", List.of(batteryLine)
        ), ProcurementApi.PurchaseOrderView.class);
        assertThat(purchase.lines()).hasSize(1);
        ProcurementApi.PurchaseOrderLineView purchaseLine = purchase.lines().get(0);
        assertThat(purchaseLine.categoryName()).isEqualTo("动力电池");
        assertThat(purchaseLine.unitPrice()).isEqualByComparingTo("300000.00");
        assertThat(purchaseLine.batteryBoxManufacturer()).isEqualTo("电池箱厂 A");
        assertThat(purchaseLine.batteryBoxModel()).isEqualTo("BOX-282");
        assertThat(purchaseLine.batteryPackManufacturer()).isEqualTo("电池包厂 B");
        assertThat(purchaseLine.batteryEnergyKwh()).isEqualByComparingTo("282.000");
        assertThat(purchaseLine.policies()).isEmpty();

        purchase = post("/api/ivs/purchase-orders/" + purchase.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        ProcurementApi.PurchaseReceiptView receipt = post(
                "/api/ivs/purchase-orders/" + purchase.id() + "/receive", Map.of(
                        "warehouseId", warehouse.id().toString(),
                        "lines", List.of(Map.of(
                                "orderLineId", purchase.lines().get(0).id().toString(),
                                "quantity", 1,
                                "serialNos", List.of("BAT-SN-TEST-001"),
                                "unitPrice", 300000))
                ), ProcurementApi.PurchaseReceiptView.class);
        assertThat(receipt.lines()).hasSize(1);
        assertThat(receipt.lines().get(0).batteryBoxManufacturer()).isEqualTo("电池箱厂 A");
        assertThat(receipt.lines().get(0).batteryPackManufacturer()).isEqualTo("电池包厂 B");

        MasterdataApi.SerialView serial = serialByKeyword("BAT-SN-TEST-001");
        assertThat(serial.status()).isEqualTo(SerialStatus.IN_STOCK);
        assertThat(serial.categoryName()).isEqualTo("动力电池");

        SalesApi.SalesOrderView sales = post("/api/ivs/sales-orders", Map.of(
                "customerId", customer.id().toString(),
                "warehouseId", warehouse.id().toString(),
                "externalOrderNo", "BAT-SO-001",
                "lines", List.of(Map.of(
                        "productId", battery.id().toString(),
                        "quantity", 1,
                        "unitPrice", 320000,
                        "serialIds", List.of(serial.id().toString()),
                        "batteryBoxManufacturer", "电池箱厂 A",
                        "batteryBoxModel", "BOX-282",
                        "batteryBoxUnitPrice", 110000,
                        "batteryPackManufacturer", "电池包厂 B",
                        "batteryEnergyKwh", 282,
                        "batteryPackUnitPrice", 210000))
        ), SalesApi.SalesOrderView.class);
        SalesApi.SalesOrderLineView salesLine = sales.lines().get(0);
        assertThat(salesLine.categoryName()).isEqualTo("动力电池");
        assertThat(salesLine.unitPrice()).isEqualByComparingTo("320000.00");
        assertThat(salesLine.batteryBoxUnitPrice()).isEqualByComparingTo("110000.00");
        assertThat(salesLine.batteryPackUnitPrice()).isEqualByComparingTo("210000.00");

        sales = post("/api/ivs/sales-orders/" + sales.id() + "/confirm", Map.of(), SalesApi.SalesOrderView.class);
        sales = post("/api/ivs/sales-orders/" + sales.id() + "/lock", Map.of(), SalesApi.SalesOrderView.class);
        assertThat(sales.status()).isEqualTo(SalesOrderStatus.CONFIRMED);
        assertThat(serialByKeyword("BAT-SN-TEST-001").status()).isEqualTo(SerialStatus.LOCKED);

        Map[] candidates = restTemplate.getForEntity("/api/ivs/retail-candidates", Map[].class).getBody();
        assertThat(candidates).isNotNull().isEmpty();
        ResponseEntity<Map> retailRejected = restTemplate.postForEntity("/api/ivs/retails", Map.of(
                "retailDate", "2026-11-04",
                "invoiceIssuedAt", "2026-11-01T02:00:00Z",
                "serialId", serial.id().toString(),
                "userName", "测试用户",
                "userMobile", "13800000000"
        ), Map.class);
        assertThat(retailRejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(retailRejected.getBody()).isNotNull();
        assertThat(retailRejected.getBody().get("code")).isEqualTo("RETAIL_BATTERY_NOT_ALLOWED");

        SalesApi.SalesDeliveryView delivery = post("/api/ivs/sales-orders/" + sales.id() + "/deliver", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of(
                        "orderLineId", sales.lines().get(0).id().toString(),
                        "serialIds", List.of(serial.id().toString())))
        ), SalesApi.SalesDeliveryView.class);
        assertThat(delivery.amount()).isEqualByComparingTo("320000.00");
        assertThat(delivery.costAmount()).isEqualByComparingTo("300000.00");
        assertThat(delivery.grossProfit()).isEqualByComparingTo("20000.00");
        assertThat(serialByKeyword("BAT-SN-TEST-001").status()).isEqualTo(SerialStatus.DELIVERED);

        InventoryApi.BalanceView[] balances = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + warehouse.id(), InventoryApi.BalanceView[].class).getBody();
        assertThat(balances).isNotNull().hasSize(1);
        assertThat(balances[0].onHandQty()).isEqualByComparingTo("0.000");
        assertThat(balances[0].lockedQty()).isEqualByComparingTo("0.000");
        assertThat(balances[0].availableQty()).isEqualByComparingTo("0.000");
    }

    private Map<String, Object> withPolicy(Map<String, Object> line, List<UUID> policyIds) {
        Map<String, Object> copy = new HashMap<>(line);
        copy.put("policyIds", policyIds.stream().map(UUID::toString).toList());
        return copy;
    }

    private MasterdataApi.SerialView serialByKeyword(String keyword) {
        MasterdataApi.SerialView[] serials = restTemplate.getForEntity(
                "/api/ivs/product-serials?keyword=" + keyword, MasterdataApi.SerialView[].class).getBody();
        assertThat(serials).isNotNull().hasSize(1);
        return serials[0];
    }

    private MasterdataApi.CategoryView createCategory(String code, String name, TrackingMode mode,
                                                      String template, String unit) {
        return post("/api/ivs/product-categories", Map.of("code", code, "name", name,
                "trackingMode", mode.name(), "statusTemplate", template, "unit", unit, "sortOrder", 0),
                MasterdataApi.CategoryView.class);
    }

    private MasterdataApi.ProductView createProduct(String name, UUID categoryId) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryId", categoryId.toString());
        body.put("unit", "组");
        body.put("brand", "测试电池厂");
        body.put("series", "282kWh");
        body.put("model", "B282");
        body.put("minPrice", 100000);
        body.put("salePriceGuide", 320000);
        return post("/api/ivs/products", body, MasterdataApi.ProductView.class);
    }

    private MasterdataApi.WarehouseView createWarehouse(String code, String name) {
        return post("/api/ivs/warehouses", Map.of("code", code, "name", name,
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
    }

    private MasterdataApi.SupplierView createSupplier(String name, SupplierType type) {
        return post("/api/ivs/suppliers", Map.of("name", name, "supplierType", type.name()),
                MasterdataApi.SupplierView.class);
    }

    private MasterdataApi.CustomerView createCustomer(String name, CustomerType type) {
        return post("/api/ivs/customers", Map.of("name", name, "customerType", type.name(),
                "creditLimit", 5000000), MasterdataApi.CustomerView.class);
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}
