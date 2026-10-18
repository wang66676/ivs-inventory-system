package com.ivs.app;

import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.procurement.purchase.api.ProcurementApi;
import com.ivs.inventory.stock.api.InventoryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 采购收货时直接录入 VIN 与单车采购价，系统自动建立 VIN 档案。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VinProcurementIntegrationTest {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() { TestDatabaseCleaner.clean(jdbcTemplate); TestAuthSupport.authenticate(restTemplate); }

    @Test
    void 收货时自动建立VIN档案并按每台采购价入账() {
        var supplier = post("/api/ivs/suppliers", Map.of("name", "VIN整车厂", "supplierType", SupplierType.OEM.name()),
                MasterdataApi.SupplierView.class);
        var warehouse = post("/api/ivs/warehouses", Map.of("code", "VINWH", "name", "VIN整车仓",
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
        var category = post("/api/ivs/product-categories", Map.of("code", "VINCAT", "name", "VIN整车",
                "trackingMode", TrackingMode.SERIAL.name(), "statusTemplate", "VEHICLE", "unit", "台", "sortOrder", 0),
                MasterdataApi.CategoryView.class);
        var product = post("/api/ivs/products", Map.of("name", "VIN测试车", "categoryId", category.id().toString(),
                "unit", "台", "minPrice", 0), MasterdataApi.ProductView.class);
        var order = post("/api/ivs/purchase-orders", Map.of("supplierId", supplier.id().toString(),
                "warehouseId", warehouse.id().toString(), "lines", List.of(Map.of("productId", product.id().toString(),
                        "quantity", 2, "unitPrice", 400000, "taxRate", new BigDecimal("0.13")))),
                ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(), ProcurementApi.PurchaseOrderView.class);
        UUID lineId = order.lines().get(0).id();
        Map<String, Object> body = new HashMap<>();
        body.put("warehouseId", warehouse.id().toString());
        body.put("lines", List.of(
                Map.of("orderLineId", lineId.toString(), "quantity", 1, "serialNos", List.of("LZZVIN2026000001"), "unitPrice", 410000),
                Map.of("orderLineId", lineId.toString(), "quantity", 1, "serialNos", List.of("LZZVIN2026000002"), "unitPrice", 430000)
        ));
        var receipt = post("/api/ivs/purchase-orders/" + order.id() + "/receive", body,
                ProcurementApi.PurchaseReceiptView.class);
        assertThat(receipt.amount()).isEqualByComparingTo("840000.00");

        MasterdataApi.SerialView[] serials = restTemplate.getForEntity(
                "/api/ivs/product-serials?keyword=LZZVIN202600000", MasterdataApi.SerialView[].class).getBody();
        assertThat(serials).isNotNull().hasSize(2);
        assertThat(serials).allSatisfy(row -> assertThat(row.status()).isEqualTo(SerialStatus.IN_STOCK));
        Map<String, BigDecimal> prices = new HashMap<>();
        for (var serial : serials) prices.put(serial.serialNo(), serial.purchasePrice());
        assertThat(prices.get("LZZVIN2026000001")).isEqualByComparingTo("410000.00");
        assertThat(prices.get("LZZVIN2026000002")).isEqualByComparingTo("430000.00");

        InventoryApi.BalanceView[] balances = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + warehouse.id(), InventoryApi.BalanceView[].class).getBody();
        assertThat(balances).isNotNull().hasSize(1);
        assertThat(balances[0].amount()).isEqualByComparingTo("840000.00");
    }

    private <T> T post(String url, Object body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}

