package com.ivs.app;

import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.policy.fund.api.RebateFundApi;
import com.ivs.procurement.purchase.api.ProcurementApi;
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

/**
 * M4 返利资金池验收：一个池按厂家分账、只能抵同厂家车款、不允许预抵扣、
 * 余额分批使用、入库单确认时抵扣、抵扣可追溯与可回补。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RebateFundIntegrationTest {

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
    void 入池按厂家分账且总余额等于各厂家之和() {
        UUID supplierA = createSupplier("某整车厂 A", SupplierType.OEM);
        UUID supplierB = createSupplier("某整车厂 B", SupplierType.OEM);

        credit(supplierA, new BigDecimal("30000"), "OEM_REBATE", "REB-2026-10-A");
        RebateFundApi.PoolView pool = credit(supplierB, new BigDecimal("12000"), "RED_INVOICE", "INV-R-0001");

        assertThat(pool.balanceAmount()).isEqualByComparingTo("42000.00");
        assertThat(pool.totalInAmount()).isEqualByComparingTo("42000.00");
        assertThat(pool.allowPreOffset()).isFalse();
        assertThat(pool.suppliers()).hasSize(2);
        assertThat(balanceOf(pool, supplierA)).isEqualByComparingTo("30000.00");
        assertThat(balanceOf(pool, supplierB)).isEqualByComparingTo("12000.00");
    }

    @Test
    void 入库单确认时抵扣且余额分批使用() {
        UUID supplierA = createSupplier("某整车厂 A", SupplierType.OEM);
        UUID warehouse = createWarehouse("WH-F1", "整车仓");
        UUID product = createQuantityProduct("配件", warehouse);
        credit(supplierA, new BigDecimal("30000"), "OEM_REBATE", "REB-2026-10-A");
        UUID creditTxnId = txns(supplierA).get(0).id();

        // 第一张单 20,000：余额 30,000 → 一次抵 20,000，剩 10,000
        ProcurementApi.PurchaseReceiptView first = receive(supplierA, warehouse, product,
                new BigDecimal("10"), new BigDecimal("2000"), List.of(creditTxnId));
        assertThat(first.amount()).isEqualByComparingTo("20000.00");
        assertThat(first.offsetAmount()).isEqualByComparingTo("20000.00");
        assertThat(first.netAmount()).isEqualByComparingTo("0.00");
        assertThat(factoryBalance(supplierA)).isEqualByComparingTo("10000.00");

        // 第二张单 15,000：余额只剩 10,000 → 抵 10,000（余额分批使用，抵完为止）
        ProcurementApi.PurchaseReceiptView second = receive(supplierA, warehouse, product,
                new BigDecimal("15"), new BigDecimal("1000"), List.of(creditTxnId));
        assertThat(second.offsetAmount()).isEqualByComparingTo("10000.00");
        assertThat(second.netAmount()).isEqualByComparingTo("5000.00");
        assertThat(factoryBalance(supplierA)).isEqualByComparingTo("0.00");

        // 抵扣单可追溯：明细指向具体的入池记录
        RebateFundApi.OffsetView offset = restTemplate.getForEntity(
                "/api/ivs/rebate-fund-pool/offsets?supplierId=" + supplierA, RebateFundApi.OffsetView[].class)
                .getBody()[0];
        assertThat(offset.lines()).isNotEmpty();
        assertThat(offset.lines().get(0).fundTxnId()).isEqualTo(creditTxnId);
        assertThat(offset.lines().get(0).sourceNo()).isEqualTo("REB-2026-10-A");
    }

    @Test
    void 余额为零时不允许抵扣_不允许预抵扣() {
        UUID supplierA = createSupplier("某整车厂 A", SupplierType.OEM);
        UUID warehouse = createWarehouse("WH-F2", "整车仓");
        UUID product = createQuantityProduct("配件", warehouse);

        // 传一个不存在的入池记录，模拟「没有已到账返利却尝试抵扣」
        ResponseEntity<Map> response = receiveRaw(supplierA, warehouse, product,
                new BigDecimal("5"), new BigDecimal("1000"), List.of(UUID.randomUUID()));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("REBATE_POOL_EMPTY");
    }

    @Test
    void 跨厂家不能使用别人的返利() {
        UUID supplierA = createSupplier("某整车厂 A", SupplierType.OEM);
        UUID supplierB = createSupplier("某整车厂 B", SupplierType.OEM);
        UUID warehouse = createWarehouse("WH-F3", "整车仓");
        UUID product = createQuantityProduct("配件", warehouse);
        credit(supplierA, new BigDecimal("30000"), "OEM_REBATE", "REB-2026-10-A");
        UUID creditOfA = txns(supplierA).get(0).id();
        credit(supplierB, new BigDecimal("1000"), "OEM_REBATE", "REB-2026-10-B");

        // 用 A 的入池记录抵扣 B 的采购单：所选记录不属于 B → 拒绝
        ResponseEntity<Map> response = receiveRaw(supplierB, warehouse, product,
                new BigDecimal("5"), new BigDecimal("1000"), List.of(creditOfA));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("REBATE_CREDIT_SELECTION_INSUFFICIENT");
        // B 的余额未被使用
        assertThat(factoryBalance(supplierB)).isEqualByComparingTo("1000.00");
    }

    @Test
    void 取消抵扣回补余额与入池记录() {
        UUID supplierA = createSupplier("某整车厂 A", SupplierType.OEM);
        UUID warehouse = createWarehouse("WH-F4", "整车仓");
        UUID product = createQuantityProduct("配件", warehouse);
        credit(supplierA, new BigDecimal("30000"), "OEM_REBATE", "REB-2026-10-A");
        UUID creditTxnId = txns(supplierA).get(0).id();

        ProcurementApi.PurchaseReceiptView receipt = receive(supplierA, warehouse, product,
                new BigDecimal("10"), new BigDecimal("2000"), List.of(creditTxnId));
        assertThat(receipt.offsetAmount()).isEqualByComparingTo("20000.00");
        assertThat(factoryBalance(supplierA)).isEqualByComparingTo("10000.00");

        RebateFundApi.OffsetView offset = restTemplate.getForEntity(
                "/api/ivs/rebate-fund-pool/offsets?supplierId=" + supplierA, RebateFundApi.OffsetView[].class)
                .getBody()[0];
        RebateFundApi.OffsetView cancelled = post("/api/ivs/rebate-fund-pool/offsets/" + offset.id() + "/cancel",
                Map.of(), RebateFundApi.OffsetView.class);

        assertThat(cancelled.status().name()).isEqualTo("CANCELLED");
        assertThat(factoryBalance(supplierA)).isEqualByComparingTo("30000.00");
        assertThat(txns(supplierA).stream()
                .filter(txn -> txn.direction().name().equals("IN") && txn.remainingAmount().signum() > 0)
                .findFirst().orElseThrow().remainingAmount()).isEqualByComparingTo("30000.00");
    }

    @Test
    void 大额人工调整资金池需审批通过后入池() {
        UUID supplierId = createSupplier("审批测试整车厂", SupplierType.OEM);
        RebateFundApi.PoolView requested = post("/api/ivs/rebate-fund-pool/credits/request", Map.of(
                "supplierId", supplierId.toString(), "amount", 15000,
                "sourceType", "ADJUST_IN", "sourceNo", "ADJ-001", "remark", "厂家到账补充"),
                RebateFundApi.PoolView.class);
        assertThat(requested.balanceAmount()).isEqualByComparingTo("0.00");

        com.ivs.platform.approval.ApprovalTaskService.TaskView[] pending = restTemplate.getForEntity(
                "/api/ivs/approval-tasks?status=PENDING",
                com.ivs.platform.approval.ApprovalTaskService.TaskView[].class).getBody();
        var task = java.util.Arrays.stream(pending).filter(item -> item.taskType().equals("FUND_POOL_CREDIT"))
                .findFirst().orElseThrow();
        assertThat(task.amount()).isEqualByComparingTo("15000.00");
        post("/api/ivs/approval-actions/" + task.id() + "/approve", Map.of(),
                com.ivs.platform.approval.ApprovalTaskService.TaskView.class);

        assertThat(factoryBalance(supplierId)).isEqualByComparingTo("15000.00");
        assertThat(txns(supplierId)).anySatisfy(txn -> {
            assertThat(txn.direction().name()).isEqualTo("IN");
            assertThat(txn.sourceNo()).isEqualTo("ADJ-001");
            assertThat(txn.amount()).isEqualByComparingTo("15000.00");
        });
    }

    /* ===================== 辅助方法 ===================== */

    private RebateFundApi.PoolView credit(UUID supplierId, BigDecimal amount, String sourceType, String sourceNo) {
        return post("/api/ivs/rebate-fund-pool/credits", Map.of(
                "supplierId", supplierId.toString(), "amount", amount,
                "sourceType", sourceType, "sourceNo", sourceNo), RebateFundApi.PoolView.class);
    }

    private List<RebateFundApi.TxnView> txns(UUID supplierId) {
        return List.of(restTemplate.getForEntity("/api/ivs/rebate-fund-pool/txns?supplierId=" + supplierId,
                RebateFundApi.TxnView[].class).getBody());
    }

    private BigDecimal factoryBalance(UUID supplierId) {
        RebateFundApi.PoolView pool = restTemplate.getForEntity("/api/ivs/rebate-fund-pool",
                RebateFundApi.PoolView.class).getBody();
        assertThat(pool).isNotNull();
        return balanceOf(pool, supplierId);
    }

    private BigDecimal balanceOf(RebateFundApi.PoolView pool, UUID supplierId) {
        return pool.suppliers().stream().filter(item -> item.supplierId().equals(supplierId))
                .findFirst().map(RebateFundApi.SupplierBalanceView::balanceAmount)
                .orElse(BigDecimal.ZERO);
    }

    private UUID createSupplier(String name, SupplierType type) {
        MasterdataApi.SupplierView supplier = post("/api/ivs/suppliers",
                Map.of("name", name, "supplierType", type.name()), MasterdataApi.SupplierView.class);
        return supplier.id();
    }

    private UUID createWarehouse(String code, String name) {
        return post("/api/ivs/warehouses", Map.of("code", code, "name", name, "type", WarehouseType.MAIN.name()),
                MasterdataApi.WarehouseView.class).id();
    }

    private UUID createQuantityProduct(String name, UUID warehouse) {
        MasterdataApi.CategoryView category = post("/api/ivs/product-categories", Map.of(
                "code", "PART", "name", "配件", "trackingMode", TrackingMode.QUANTITY.name(),
                "statusTemplate", "SIMPLE", "unit", "件", "sortOrder", 0), MasterdataApi.CategoryView.class);
        MasterdataApi.ProductView product = post("/api/ivs/products", Map.of(
                "name", name, "categoryId", category.id().toString(), "unit", "件", "minPrice", 0),
                MasterdataApi.ProductView.class);
        return product.id();
    }

    private ProcurementApi.PurchaseReceiptView receive(UUID supplierId, UUID warehouseId, UUID productId,
                                                       BigDecimal quantity, BigDecimal unitPrice,
                                                       List<UUID> offsetTxnIds) {
        ResponseEntity<ProcurementApi.PurchaseReceiptView> response = restTemplate.postForEntity(
                "/api/ivs/purchase-orders/" + createApprovedOrder(supplierId, warehouseId, productId, quantity, unitPrice)
                        + "/receive", receiveBody(warehouseId, productId, quantity, offsetTxnIds),
                ProcurementApi.PurchaseReceiptView.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return response.getBody();
    }

    private ResponseEntity<Map> receiveRaw(UUID supplierId, UUID warehouseId, UUID productId,
                                           BigDecimal quantity, BigDecimal unitPrice, List<UUID> offsetTxnIds) {
        return restTemplate.postForEntity(
                "/api/ivs/purchase-orders/" + createApprovedOrder(supplierId, warehouseId, productId, quantity, unitPrice)
                        + "/receive", receiveBody(warehouseId, productId, quantity, offsetTxnIds), Map.class);
    }

    private Map<String, Object> receiveBody(UUID warehouseId, UUID productId, BigDecimal quantity,
                                            List<UUID> offsetTxnIds) {
        ProcurementApi.PurchaseOrderView order = restTemplate.getForEntity(
                "/api/ivs/purchase-orders", ProcurementApi.PurchaseOrderView[].class).getBody()[0];
        UUID lineId = order.lines().get(0).id();
        Map<String, Object> body = new HashMap<>();
        body.put("warehouseId", warehouseId.toString());
        body.put("lines", List.of(Map.of("orderLineId", lineId.toString(), "quantity", quantity)));
        if (offsetTxnIds != null && !offsetTxnIds.isEmpty()) {
            body.put("offsetTxnIds", offsetTxnIds.stream().map(UUID::toString).toList());
        }
        return body;
    }

    private UUID createApprovedOrder(UUID supplierId, UUID warehouseId, UUID productId,
                                     BigDecimal quantity, BigDecimal unitPrice) {
        ProcurementApi.PurchaseOrderView order = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplierId.toString(),
                "warehouseId", warehouseId.toString(),
                "lines", List.of(Map.of("productId", productId.toString(), "quantity", quantity,
                        "unitPrice", unitPrice))), ProcurementApi.PurchaseOrderView.class);
        ProcurementApi.PurchaseOrderView approved = post(
                "/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(), ProcurementApi.PurchaseOrderView.class);
        return approved.id();
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}
