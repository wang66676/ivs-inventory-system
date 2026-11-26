package com.ivs.app;

import com.ivs.procurement.purchase.api.ProcurementApi;
import com.ivs.sales.order.api.SalesApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 库存与统计看板必须直接汇总实际收货和实际出库。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InventoryStatisticsIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbc);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 统计看板按日期和仓库类型汇总采购销售数量() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String purchaseOrderNo = "CG2512" + suffix;
        String salesOrderNo = "XS202512" + suffix;
        Map category = post("/api/ivs/product-categories", Map.of(
                "code", "STAT-" + suffix, "name", "统计测试商品", "trackingMode", "QUANTITY",
                "statusTemplate", "SIMPLE", "unit", "件", "sortOrder", 0), Map.class);
        Map product = post("/api/ivs/products", Map.of(
                "name", "统计测试商品", "categoryId", category.get("id"), "unit", "件",
                "minPrice", 100, "purchasePriceRef", 90), Map.class);
        Map warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "STAT-WH-" + suffix, "name", "统计数据仓", "type", "MAIN"), Map.class);
        Map supplier = post("/api/ivs/suppliers", Map.of(
                "name", "统计供应商-" + suffix, "supplierType", "OTHER"), Map.class);
        Map customer = post("/api/ivs/customers", Map.of(
                "name", "统计客户-" + suffix, "customerType", "COMPANY",
                "creditLimit", 1000000, "paymentTermDays", 30), Map.class);

        Map order = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.get("id"), "externalOrderNo", purchaseOrderNo,
                "warehouseId", warehouse.get("id"),
                "lines", List.of(Map.of("productId", product.get("id"), "quantity", 5,
                        "unitPrice", 100, "policyIds", List.of()))), Map.class);
        Map approved = post("/api/ivs/purchase-orders/" + order.get("id") + "/approve", Map.of(), Map.class);
        Map line = ((List<Map>) approved.get("lines")).get(0);
        post("/api/ivs/purchase-orders/" + order.get("id") + "/receive", Map.of(
                "warehouseId", warehouse.get("id"), "remark", "统计收货",
                "lines", List.of(Map.of("orderLineId", line.get("id"), "quantity", 5, "unitPrice", 100)),
                "offsetTxnIds", List.of()), Map.class);

        Map salesOrder = post("/api/ivs/sales-orders", Map.of(
                "externalOrderNo", salesOrderNo, "customerId", customer.get("id"),
                "warehouseId", warehouse.get("id"), "remark", "统计销售",
                "paymentMode", "CASH", "downPayment", 0, "installmentCount", 0,
                "lines", List.of(Map.of("productId", product.get("id"), "quantity", 2,
                        "unitPrice", 120, "serialIds", List.of()))), Map.class);
        post("/api/ivs/sales-orders/" + salesOrder.get("id") + "/confirm", Map.of(), Map.class);
        post("/api/ivs/sales-orders/" + salesOrder.get("id") + "/lock", Map.of(), Map.class);
        Map refreshed = get("/api/ivs/sales-orders/" + salesOrder.get("id"), Map.class);
        Map salesLine = ((List<Map>) refreshed.get("lines")).get(0);
        post("/api/ivs/sales-orders/" + salesOrder.get("id") + "/deliver", Map.of(
                "warehouseId", warehouse.get("id"), "remark", "统计出库",
                "lines", List.of(Map.of("orderLineId", salesLine.get("id"), "quantity", 2))), Map.class);

        Map[] filteredOrders = get("/api/ivs/sales-orders?customerId=" + customer.get("id")
                + "&status=DELIVERED&keyword=" + salesOrderNo, Map[].class);
        assertThat(filteredOrders).hasSize(1);
        String from = "2025-12-01";
        String to = "2025-12-31";
        Map result = get("/api/ivs/statistics/inventory?from=" + from + "&to=" + to
                + "&businessScope=VEHICLE", Map.class);
        Map summary = (Map) result.get("summary");
        assertThat(new BigDecimal(String.valueOf(summary.get("totalPurchaseQuantity"))))
                .isEqualByComparingTo("5.000");
        assertThat(new BigDecimal(String.valueOf(summary.get("totalSalesQuantity"))))
                .isEqualByComparingTo("2.000");
        assertThat(summary.get("purchaseOrderCount")).isEqualTo(1);
        assertThat(summary.get("salesOrderCount")).isEqualTo(1);
        List<Map> warehouseRows = (List<Map>) result.get("byWarehouseType");
        Map main = warehouseRows.stream().filter(row -> "MAIN".equals(row.get("warehouseType"))).findFirst().orElseThrow();
        assertThat(new BigDecimal(String.valueOf(main.get("purchaseQuantity")))).isEqualByComparingTo("5.000");
        assertThat(new BigDecimal(String.valueOf(main.get("salesQuantity")))).isEqualByComparingTo("2.000");
        List<Map> trend = (List<Map>) result.get("trend");
        assertThat(trend).anySatisfy(row -> {
            assertThat(row.get("period")).isEqualTo("2025-12");
            assertThat(new BigDecimal(String.valueOf(row.get("purchaseQuantity")))).isEqualByComparingTo("5.000");
            assertThat(new BigDecimal(String.valueOf(row.get("salesQuantity")))).isEqualByComparingTo("2.000");
        });
    }

    private <T> T post(String path, Map body, Class<T> type) {
        return (T) restTemplate.postForEntity(path, body, type).getBody();
    }

    private <T> T get(String path, Class<T> type) {
        return restTemplate.getForEntity(path, type).getBody();
    }
}
