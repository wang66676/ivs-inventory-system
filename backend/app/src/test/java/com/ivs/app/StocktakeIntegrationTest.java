package com.ivs.app;

import com.ivs.inventory.stock.api.InventoryApi;
import com.ivs.inventory.stock.StockMoveType;
import com.ivs.inventory.stocktake.api.StocktakeApi;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.procurement.purchase.api.ProcurementApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M5：库存盘点账面快照、差异过账、台账余额一致与并发变化拦截。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StocktakeIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 盘点差异过账写入台账并更新余额() {
        Fixture fixture = fixture("0.13");

        StocktakeApi.StocktakeView draft = post("/api/ivs/stocktakes",
                Map.of("warehouseId", fixture.warehouseId().toString(), "remark", "月度盘点"),
                StocktakeApi.StocktakeView.class);
        assertThat(draft.stocktakeNo()).startsWith("ST-");
        assertThat(draft.status().name()).isEqualTo("DRAFT");
        assertThat(draft.lines()).hasSize(1);
        assertThat(draft.lines().get(0).bookQty()).isEqualByComparingTo("10.000");
        assertThat(draft.lines().get(0).actualQty()).isEqualByComparingTo("10.000");

        StocktakeApi.StocktakeView updated = put("/api/ivs/stocktakes/" + draft.id(), Map.of(
                "lines", List.of(Map.of("productId", fixture.productId().toString(), "batchNo", "-",
                        "actualQty", 8, "unitCost", 100, "differenceReason", "破损报废"))),
                StocktakeApi.StocktakeView.class);
        assertThat(updated.differenceCount()).isEqualTo(1);
        assertThat(updated.totalDifferenceQty()).isEqualByComparingTo("-2.000");

        StocktakeApi.StocktakeView posted = post("/api/ivs/stocktakes/" + draft.id() + "/post", Map.of(),
                StocktakeApi.StocktakeView.class);
        assertThat(posted.status().name()).isEqualTo("POSTED");

        InventoryApi.BalanceView[] balances = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + fixture.warehouseId(),
                InventoryApi.BalanceView[].class).getBody();
        assertThat(balances).isNotNull().hasSize(1);
        assertThat(balances[0].onHandQty()).isEqualByComparingTo("8.000");

        InventoryApi.LedgerView[] ledger = restTemplate.getForEntity(
                "/api/ivs/inventory/ledger?productId=" + fixture.productId(),
                InventoryApi.LedgerView[].class).getBody();
        assertThat(ledger).isNotNull();
        var stocktakeLedgers = java.util.Arrays.stream(ledger)
                .filter(row -> row.moveType() == StockMoveType.STOCKTAKE_ADJUST).toList();
        assertThat(stocktakeLedgers).hasSize(1);
        assertThat(stocktakeLedgers.get(0).direction().name()).isEqualTo("OUT");
        assertThat(stocktakeLedgers.get(0).quantity()).isEqualByComparingTo("2.000");
        assertThat(stocktakeLedgers.get(0).bizNo()).isEqualTo(draft.stocktakeNo());

        var again = restTemplate.postForEntity("/api/ivs/stocktakes/" + draft.id() + "/post", Map.of(), Map.class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(again.getBody().get("code")).isEqualTo("STOCKTAKE_STATUS_INVALID");
    }

    @Test
    void 盘点期间库存变化时拒绝过账() {
        Fixture fixture = fixture("0.13");
        StocktakeApi.StocktakeView draft = post("/api/ivs/stocktakes",
                Map.of("warehouseId", fixture.warehouseId().toString()), StocktakeApi.StocktakeView.class);
        put("/api/ivs/stocktakes/" + draft.id(), Map.of(
                "lines", List.of(Map.of("productId", fixture.productId().toString(), "batchNo", "-",
                        "actualQty", 8, "unitCost", 100, "differenceReason", "盘亏"))),
                StocktakeApi.StocktakeView.class);

        post("/api/ivs/stocks/adjust", Map.of("productId", fixture.productId().toString(),
                "warehouseId", fixture.warehouseId().toString(), "batchNo", "-",
                "targetQuantity", 9, "targetUnitCost", 100, "remark", "盘点期间出库"), InventoryApi.LedgerView.class);

        var response = restTemplate.postForEntity("/api/ivs/stocktakes/" + draft.id() + "/post", Map.of(), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("STOCKTAKE_BALANCE_CHANGED");
    }

    @Test
    void 大额盘点差异进入审批通过后自动过账() {
        Fixture fixture = fixture("0.13", new BigDecimal("1000"));
        StocktakeApi.StocktakeView draft = post("/api/ivs/stocktakes",
                Map.of("warehouseId", fixture.warehouseId().toString(), "remark", "大额盘亏"),
                StocktakeApi.StocktakeView.class);
        put("/api/ivs/stocktakes/" + draft.id(), Map.of(
                "lines", List.of(Map.of("productId", fixture.productId().toString(), "batchNo", "-",
                        "actualQty", 0, "unitCost", 1000, "differenceReason", "大额盘亏"))),
                StocktakeApi.StocktakeView.class);

        StocktakeApi.StocktakeView pending = post("/api/ivs/stocktakes/" + draft.id() + "/post", Map.of(),
                StocktakeApi.StocktakeView.class);
        assertThat(pending.status().name()).isEqualTo("PENDING_APPROVAL");
        com.ivs.platform.approval.ApprovalTaskService.TaskView[] tasks = restTemplate.getForEntity(
                "/api/ivs/approval-tasks?status=PENDING",
                com.ivs.platform.approval.ApprovalTaskService.TaskView[].class).getBody();
        assertThat(tasks).isNotNull().anySatisfy(task -> {
            assertThat(task.businessId()).isEqualTo(draft.id());
            assertThat(task.taskType()).isEqualTo("STOCKTAKE_POST");
        });
        var task = java.util.Arrays.stream(tasks).filter(item -> item.businessId().equals(draft.id()))
                .findFirst().orElseThrow();
        post("/api/ivs/approval-actions/" + task.id() + "/approve", Map.of(),
                com.ivs.platform.approval.ApprovalTaskService.TaskView.class);

        StocktakeApi.StocktakeView posted = restTemplate.getForEntity(
                "/api/ivs/stocktakes/" + draft.id(), StocktakeApi.StocktakeView.class).getBody();
        assertThat(posted).isNotNull();
        assertThat(posted.status().name()).isEqualTo("POSTED");
        InventoryApi.BalanceView[] balances = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + fixture.warehouseId(),
                InventoryApi.BalanceView[].class).getBody();
        assertThat(balances).isNotNull().singleElement()
                .satisfies(row -> assertThat(row.onHandQty()).isEqualByComparingTo("0.000"));
    }

    private Fixture fixture(String taxRate) {
        return fixture(taxRate, new BigDecimal("100"));
    }

    private Fixture fixture(String taxRate, BigDecimal unitPrice) {
        MasterdataApi.CategoryView category = post("/api/ivs/product-categories", Map.of(
                "code", "PART-" + UUID.randomUUID().toString().substring(0, 6), "name", "配件",
                "trackingMode", TrackingMode.QUANTITY.name(), "statusTemplate", "SIMPLE",
                "unit", "件", "sortOrder", 0), MasterdataApi.CategoryView.class);
        MasterdataApi.ProductView product = post("/api/ivs/products", Map.of(
                "name", "测试配件", "categoryId", category.id().toString(), "unit", "件", "minPrice", 0),
                MasterdataApi.ProductView.class);
        MasterdataApi.WarehouseView warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "WH-" + UUID.randomUUID().toString().substring(0, 6), "name", "盘点仓",
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
        MasterdataApi.SupplierView supplier = post("/api/ivs/suppliers", Map.of(
                "name", "盘点供应商", "supplierType", SupplierType.OTHER.name()), MasterdataApi.SupplierView.class);

        ProcurementApi.PurchaseOrderView order = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.id().toString(), "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 10,
                        "unitPrice", unitPrice, "taxRate", new BigDecimal(taxRate)))), ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        post("/api/ivs/purchase-orders/" + order.id() + "/receive", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(), "quantity", 10))),
                ProcurementApi.PurchaseReceiptView.class);
        return new Fixture(product.id(), warehouse.id());
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }

    private <T> T put(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.exchange(url, org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(body), type).getBody();
    }

    private record Fixture(UUID productId, UUID warehouseId) {
    }
}

