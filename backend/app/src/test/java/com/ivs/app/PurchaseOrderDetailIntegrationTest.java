package com.ivs.app;

import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.policy.policy.api.PolicyApi;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 新建采购单：订单编号、车系、数量、单价、总计车款、可选的单项/多项返利政策。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PurchaseOrderDetailIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 新建采购单保存订单编号车系总车款与多项返利政策() {
        var supplier = createSupplier("整车厂A");
        var warehouse = createWarehouse("WH-A");
        var secondWarehouse = createWarehouse("WH-B");
        var product = createProduct("Q3 牵引车");
        var first = publish(createPolicy(supplier.id(), product, "年度返利", "10000"));
        var second = publish(createPolicy(supplier.id(), product, "库存车补贴", "3000"));

        Map<String, Object> body = new HashMap<>();
        body.put("supplierId", supplier.id().toString());
        body.put("externalOrderNo", "OEM-ORDER-2026-001");
        body.put("warehouseId", warehouse.id().toString());
        body.put("lines", List.of(Map.of(
                "productId", product.id().toString(),
                "quantity", 2,
                "unitPrice", 410000,
                "configText", "客户定制：寒区包 + 独立暖风",
                "warehouseId", secondWarehouse.id().toString(),
                "policyIds", List.of(first.id().toString(), second.id().toString())
        )));

        ProcurementApi.PurchaseOrderView order = restTemplate.postForEntity(
                "/api/ivs/purchase-orders", body, ProcurementApi.PurchaseOrderView.class).getBody();

        assertThat(order).isNotNull();
        assertThat(order.externalOrderNo()).isEqualTo("OEM-ORDER-2026-001");
        assertThat(order.totalAmount()).isEqualByComparingTo("820000.00");
        assertThat(order.lines()).hasSize(1);
        var line = order.lines().get(0);
        assertThat(line.series()).isEqualTo("Q3 牵引车");
        assertThat(line.configCode()).isEqualTo("6x4-AMT");
        assertThat(line.configText()).isEqualTo("客户定制：寒区包 + 独立暖风");
        assertThat(line.warehouseId()).isEqualTo(secondWarehouse.id());
        assertThat(line.warehouseName()).isEqualTo("WH-B");
        assertThat(line.quantity()).isEqualByComparingTo("2.000");
        assertThat(line.unitPrice()).isEqualByComparingTo("410000.00");
        assertThat(line.amount()).isEqualByComparingTo("820000.00");
        assertThat(line.policies()).extracting(ProcurementApi.PolicyRefView::name)
                .containsExactly("年度返利", "库存车补贴");

        order = restTemplate.postForEntity("/api/ivs/purchase-orders/" + order.id() + "/approve",
                Map.of(), ProcurementApi.PurchaseOrderView.class).getBody();
        assertThat(order).isNotNull();
        var receipt = restTemplate.postForEntity("/api/ivs/purchase-orders/" + order.id() + "/receive",
                Map.of("warehouseId", secondWarehouse.id().toString(), "lines", List.of(Map.of(
                        "orderLineId", line.id().toString(), "serialNos",
                        List.of("LZZMULTIPOLICY0001", "LZZMULTIPOLICY0002"), "unitPrice", 410000,
                        "warehouseId", secondWarehouse.id().toString()))),
                ProcurementApi.PurchaseReceiptView.class).getBody();
        assertThat(receipt).isNotNull();

        PolicyApi.PreEntitlementView[] pres = restTemplate.getForEntity(
                "/api/ivs/pre-entitlements?orderId=" + order.id(), PolicyApi.PreEntitlementView[].class).getBody();
        assertThat(pres).isNotNull().hasSize(4);
        assertThat(java.util.Arrays.stream(pres).map(PolicyApi.PreEntitlementView::orderNo).toList())
                .containsOnly("OEM-ORDER-2026-001");
        assertThat(java.util.Arrays.stream(pres).map(PolicyApi.PreEntitlementView::serialNos).toList())
                .contains("LZZMULTIPOLICY0001", "LZZMULTIPOLICY0002");

        ProcurementApi.PurchaseOrderView loaded = restTemplate.getForEntity(
                "/api/ivs/purchase-orders/" + order.id(), ProcurementApi.PurchaseOrderView.class).getBody();
        assertThat(loaded).isNotNull();
        assertThat(loaded.externalOrderNo()).isEqualTo("OEM-ORDER-2026-001");
        assertThat(loaded.lines().get(0).policies()).hasSize(2);
    }

    @Test
    void 采购明细不能关联其他厂家的返利政策() {
        var supplier = createSupplier("整车厂A");
        var otherSupplier = createSupplier("整车厂B");
        var warehouse = createWarehouse("WH-A");
        var product = createProduct("Q3 牵引车");
        var otherPolicy = publish(createPolicy(otherSupplier.id(), product, "其他厂家政策", "5000"));

        Map<String, Object> body = new HashMap<>();
        body.put("supplierId", supplier.id().toString());
        body.put("externalOrderNo", "OEM-ORDER-2026-002");
        body.put("warehouseId", warehouse.id().toString());
        body.put("lines", List.of(Map.of(
                "productId", product.id().toString(),
                "quantity", 1,
                "unitPrice", 410000,
                "policyIds", List.of(otherPolicy.id().toString())
        )));

        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/ivs/purchase-orders", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("PURCHASE_POLICY_SUPPLIER_MISMATCH");
    }

    @Test
    void 采购政策预享受项可按VIN实销生成权益并显示实销日期() {
        var supplier = createSupplier("整车厂D");
        var warehouse = createWarehouse("WH-D");
        var product = createProduct("Q3 预享受测试车");
        var policy = publish(createPolicy(supplier.id(), product, "采购返利同步政策", "8000"));

        Map<String, Object> orderBody = new HashMap<>();
        orderBody.put("supplierId", supplier.id().toString());
        orderBody.put("externalOrderNo", "ENT-SYNC-001");
        orderBody.put("warehouseId", warehouse.id().toString());
        orderBody.put("lines", List.of(Map.of(
                "productId", product.id().toString(), "quantity", 1, "unitPrice", 400000,
                "policyIds", List.of(policy.id().toString()))));
        ProcurementApi.PurchaseOrderView order = restTemplate.postForEntity("/api/ivs/purchase-orders",
                orderBody, ProcurementApi.PurchaseOrderView.class).getBody();
        assertThat(order).isNotNull();
        order = restTemplate.postForEntity("/api/ivs/purchase-orders/" + order.id() + "/approve",
                Map.of(), ProcurementApi.PurchaseOrderView.class).getBody();
        assertThat(order).isNotNull();

        ProcurementApi.PurchaseReceiptView receipt = restTemplate.postForEntity(
                "/api/ivs/purchase-orders/" + order.id() + "/receive",
                Map.of("warehouseId", warehouse.id().toString(), "lines", List.of(Map.of(
                        "orderLineId", order.lines().get(0).id().toString(), "serialNos",
                        List.of("LZZENTITLE0000001"), "unitPrice", 400000,
                        "warehouseId", warehouse.id().toString()))),
                ProcurementApi.PurchaseReceiptView.class).getBody();
        assertThat(receipt).isNotNull();
        MasterdataApi.SerialView serial = restTemplate.getForEntity(
                "/api/ivs/product-serials?keyword=LZZENTITLE0000001", MasterdataApi.SerialView[].class)
                .getBody()[0];

        Map<String, Object> retailBody = new HashMap<>();
        retailBody.put("retailDate", "2026-10-15");
        retailBody.put("invoiceIssuedAt", "2026-10-01T02:00:00Z");
        retailBody.put("serialId", serial.id().toString());
        retailBody.put("orderId", order.id().toString());
        retailBody.put("userName", "张三");
        retailBody.put("userMobile", "13800000000");
        var retail = restTemplate.postForEntity("/api/ivs/retails", retailBody, Map.class).getBody();
        assertThat(retail).isNotNull();

        Map<String, Object> generateBody = new HashMap<>();
        generateBody.put("orderId", order.id().toString());
        generateBody.put("retailId", retail.get("id").toString());
        generateBody.put("retailDate", "2026-10-15");
        generateBody.put("serialId", serial.id().toString());
        var entitlements = restTemplate.postForEntity("/api/ivs/retail-entitlements/generate", generateBody,
                com.ivs.policy.policy.api.EntitlementApi[].class);
        assertThat(entitlements.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entitlements.getBody()).isNotNull().hasSize(1);
        assertThat(entitlements.getBody()[0].faceAmount()).isEqualByComparingTo("8000.00");
        assertThat(entitlements.getBody()[0].serialId()).isEqualTo(serial.id());

        PolicyApi.PreEntitlementView[] pres = restTemplate.getForEntity(
                "/api/ivs/pre-entitlements?orderId=" + order.id(), PolicyApi.PreEntitlementView[].class).getBody();
        assertThat(pres).isNotNull().hasSize(1);
        assertThat(pres[0].serialNos()).isEqualTo("LZZENTITLE0000001");
        assertThat(pres[0].orderNo()).isEqualTo("ENT-SYNC-001");
        assertThat(pres[0].invoiceIssuedAt()).isEqualTo(java.time.Instant.parse("2026-10-01T02:00:00Z"));
        assertThat(pres[0].retailDate()).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void 同一采购订单两个VIN分别生成权益且历史行级预享受项自动补齐() {
        var supplier = createSupplier("整车厂多VIN权益");
        var warehouse = createWarehouse("WH-MULTI-VIN");
        var product = createProduct("Q3 多VIN权益测试车");
        var policy = publish(createPolicy(supplier.id(), product, "多VIN常规折扣", "8000"));

        Map<String, Object> orderBody = new HashMap<>();
        orderBody.put("supplierId", supplier.id().toString());
        orderBody.put("externalOrderNo", "MULTI-VIN-ENT-001");
        orderBody.put("warehouseId", warehouse.id().toString());
        orderBody.put("lines", List.of(Map.of(
                "productId", product.id().toString(), "quantity", 2, "unitPrice", 400000,
                "policyIds", List.of(policy.id().toString()))));
        var order = restTemplate.postForEntity("/api/ivs/purchase-orders", orderBody,
                ProcurementApi.PurchaseOrderView.class).getBody();
        assertThat(order).isNotNull();
        restTemplate.postForEntity("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        restTemplate.postForEntity("/api/ivs/purchase-orders/" + order.id() + "/receive",
                Map.of("warehouseId", warehouse.id().toString(), "lines", List.of(Map.of(
                        "orderLineId", order.lines().get(0).id().toString(),
                        "serialNos", List.of("LZZMULTIENT0000001", "LZZMULTIENT0000002"),
                        "unitPrice", 400000, "warehouseId", warehouse.id().toString()))),
                ProcurementApi.PurchaseReceiptView.class);

        PolicyApi.PreEntitlementView[] generatedPres = restTemplate.getForEntity(
                "/api/ivs/pre-entitlements?orderId=" + order.id(),
                PolicyApi.PreEntitlementView[].class).getBody();
        assertThat(generatedPres).isNotNull().hasSize(2);
        List<UUID> serialIds = java.util.Arrays.stream(generatedPres)
                .map(PolicyApi.PreEntitlementView::serialId).toList();

        // 模拟升级前的一条行级预享受项：没有 serial_id，且同一政策只保留一条记录。
        jdbcTemplate.update("delete from ivs.policy_pre_entitlement where id = ?", generatedPres[1].id());
        jdbcTemplate.update("update ivs.policy_pre_entitlement set serial_id = null where id = ?",
                generatedPres[0].id());

        for (UUID serialId : serialIds) {
            Map<String, Object> generateBody = new HashMap<>();
            // 实销页面实际传入销售订单 ID，控制器必须根据 VIN 回到采购订单查找预享受项。
            generateBody.put("orderId", UUID.randomUUID().toString());
            generateBody.put("retailId", UUID.randomUUID().toString());
            generateBody.put("retailDate", "2026-10-15");
            generateBody.put("serialId", serialId.toString());
            var response = restTemplate.postForEntity("/api/ivs/retail-entitlements/generate", generateBody,
                    com.ivs.policy.policy.api.EntitlementApi[].class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull().hasSize(1);
            assertThat(response.getBody()[0].serialId()).isEqualTo(serialId);
            var repeated = restTemplate.postForEntity("/api/ivs/retail-entitlements/generate", generateBody,
                    com.ivs.policy.policy.api.EntitlementApi[].class);
            assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(repeated.getBody()).isNotNull().hasSize(1);
            assertThat(repeated.getBody()[0].entitlementNo()).isEqualTo(response.getBody()[0].entitlementNo());
        }

        PolicyApi.PreEntitlementView[] repairedPres = restTemplate.getForEntity(
                "/api/ivs/pre-entitlements?orderId=" + order.id(),
                PolicyApi.PreEntitlementView[].class).getBody();
        assertThat(repairedPres).isNotNull().hasSize(2);
        assertThat(java.util.Arrays.stream(repairedPres).map(PolicyApi.PreEntitlementView::serialId).toList())
                .containsExactlyInAnyOrderElementsOf(serialIds);
        assertThat(java.util.Arrays.stream(repairedPres).map(PolicyApi.PreEntitlementView::status).toList())
                .containsOnly(com.ivs.policy.policy.PreEntitlementStatus.READY);
    }

    @Test
    void 采购单可同步尚未达成条件的返利政策() {
        var supplier = createSupplier("整车厂E");
        var warehouse = createWarehouse("WH-E");
        var product = createProduct("Q3 条件政策测试车");
        Map<String, Object> policyBody = new HashMap<>();
        policyBody.put("name", "季度目标条件返利");
        policyBody.put("policyType", "PHASE_SUBSIDY");
        policyBody.put("supplierId", supplier.id().toString());
        policyBody.put("productIds", List.of(product.id().toString()));
        policyBody.put("benefitType", "AMOUNT");
        policyBody.put("benefitValue", new BigDecimal("5000"));
        policyBody.put("effFrom", LocalDate.now().minusDays(1).toString());
        policyBody.put("effTo", LocalDate.now().plusDays(30).toString());
        policyBody.put("referenceDateType", "INVOICE_DATE");
        policyBody.put("claimCycle", "MONTHLY");
        policyBody.put("claimCutoffDay", 5);
        policyBody.put("stackingRule", "ADDITIVE");
        policyBody.put("limitType", "CONDITION");
        policyBody.put("conditionDesc", "季度销量达成 300 台");
        var policy = publish(restTemplate.postForEntity("/api/ivs/policies", policyBody,
                PolicyApi.PolicyView.class).getBody());

        Map<String, Object> orderBody = new HashMap<>();
        orderBody.put("supplierId", supplier.id().toString());
        orderBody.put("externalOrderNo", "CONDITION-SYNC-001");
        orderBody.put("warehouseId", warehouse.id().toString());
        orderBody.put("lines", List.of(Map.of(
                "productId", product.id().toString(), "quantity", 1, "unitPrice", 400000,
                "policyIds", List.of(policy.id().toString()))));
        var response = restTemplate.postForEntity("/api/ivs/purchase-orders", orderBody, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID orderId = UUID.fromString(((Map<?, ?>) response.getBody()).get("id").toString());
        var orderView = restTemplate.getForEntity("/api/ivs/purchase-orders/" + orderId,
                ProcurementApi.PurchaseOrderView.class).getBody();
        assertThat(orderView).isNotNull();
        restTemplate.postForEntity("/api/ivs/purchase-orders/" + orderId + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        restTemplate.postForEntity("/api/ivs/purchase-orders/" + orderId + "/receive",
                Map.of("warehouseId", warehouse.id().toString(), "lines", List.of(Map.of(
                        "orderLineId", orderView.lines().get(0).id().toString(), "serialNos",
                        List.of("LZZCONDITION000001"), "unitPrice", 400000,
                        "warehouseId", warehouse.id().toString()))), ProcurementApi.PurchaseReceiptView.class);
        PolicyApi.PreEntitlementView[] pres = restTemplate.getForEntity(
                "/api/ivs/pre-entitlements?orderId=" + orderId, PolicyApi.PreEntitlementView[].class).getBody();
        assertThat(pres).isNotNull().hasSize(1);
        assertThat(pres[0].status().name()).isEqualTo("PENDING_RETAIL");
        PolicyApi.PolicyView refreshed = restTemplate.getForEntity("/api/ivs/policies/" + policy.id(),
                PolicyApi.PolicyView.class).getBody();
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.referenceDateType()).isEqualTo("INVOICE_DATE");
        assertThat(refreshed.pool().conditionMet()).isFalse();
    }

    @Test
    void 草稿采购单可编辑补录返利政策和收货仓库() {
        var supplier = createSupplier("整车厂C");
        var warehouse = createWarehouse("WH-C");
        var product = createProduct("Q3 财富版");
        var policy = publish(createPolicy(supplier.id(), product, "后补返利", "6000"));

        Map<String, Object> createBody = new HashMap<>();
        createBody.put("supplierId", supplier.id().toString());
        createBody.put("externalOrderNo", "OEM-EDIT-001");
        createBody.put("lines", List.of(Map.of(
                "productId", product.id().toString(), "quantity", 1, "unitPrice", 400000,
                "configText", "人工配置")));
        ProcurementApi.PurchaseOrderView created = restTemplate.postForEntity(
                "/api/ivs/purchase-orders", createBody, ProcurementApi.PurchaseOrderView.class).getBody();
        assertThat(created).isNotNull();
        assertThat(created.lines().get(0).policies()).isEmpty();
        assertThat(created.lines().get(0).warehouseId()).isNull();

        Map<String, Object> updateBody = new HashMap<>();
        updateBody.put("supplierId", supplier.id().toString());
        updateBody.put("externalOrderNo", "OEM-EDIT-001");
        updateBody.put("lines", List.of(Map.of(
                "productId", product.id().toString(), "quantity", 1, "unitPrice", 400000,
                "configText", "人工配置", "warehouseId", warehouse.id().toString(),
                "policyIds", List.of(policy.id().toString()))));
        ProcurementApi.PurchaseOrderView updated = restTemplate.exchange(
                "/api/ivs/purchase-orders/" + created.id(), org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(updateBody), ProcurementApi.PurchaseOrderView.class).getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.lines().get(0).warehouseId()).isEqualTo(warehouse.id());
        assertThat(updated.lines().get(0).warehouseName()).isEqualTo("WH-C");
        assertThat(updated.lines().get(0).policies()).extracting(ProcurementApi.PolicyRefView::name)
                .containsExactly("后补返利");
    }

    private MasterdataApi.SupplierView createSupplier(String name) {
        return restTemplate.postForEntity("/api/ivs/suppliers", Map.of(
                "name", name, "supplierType", SupplierType.OEM.name()
        ), MasterdataApi.SupplierView.class).getBody();
    }

    private MasterdataApi.WarehouseView createWarehouse(String code) {
        return restTemplate.postForEntity("/api/ivs/warehouses", Map.of(
                "code", code, "name", code, "type", WarehouseType.MAIN.name()
        ), MasterdataApi.WarehouseView.class).getBody();
    }

    private MasterdataApi.ProductView createProduct(String series) {
        var category = restTemplate.postForEntity("/api/ivs/product-categories", Map.of(
                "code", "TRUCK-" + UUID.randomUUID().toString().substring(0, 8),
                "name", "整车",
                "trackingMode", TrackingMode.SERIAL.name(),
                "statusTemplate", "VEHICLE",
                "unit", "台",
                "sortOrder", 0
        ), MasterdataApi.CategoryView.class).getBody();
        return restTemplate.postForEntity("/api/ivs/products", Map.of(
                "name", "Q3 牵引车",
                "series", series,
                "model", "6x4",
                "configCode", "6x4-AMT",
                "categoryId", category.id().toString(),
                "unit", "台"
        ), MasterdataApi.ProductView.class).getBody();
    }

    private PolicyApi.PolicyView createPolicy(UUID supplierId, MasterdataApi.ProductView product, String name,
                                               String amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("policyType", "PHASE_SUBSIDY");
        body.put("supplierId", supplierId.toString());
        body.put("categoryId", product.categoryId().toString());
        body.put("productIds", List.of(product.id().toString()));
        body.put("benefitType", "AMOUNT");
        body.put("benefitValue", new BigDecimal(amount));
        body.put("effFrom", LocalDate.now().minusDays(1).toString());
        body.put("effTo", LocalDate.now().plusDays(30).toString());
        body.put("referenceDateType", "RETAIL_DATE");
        body.put("claimCycle", "MONTHLY");
        body.put("claimCutoffDay", 5);
        body.put("priority", 0);
        body.put("stackingRule", "ADDITIVE");
        body.put("limitType", "TIME_ONLY");
        return restTemplate.postForEntity("/api/ivs/policies", body, PolicyApi.PolicyView.class).getBody();
    }

    private PolicyApi.PolicyView publish(PolicyApi.PolicyView policy) {
        return restTemplate.postForEntity("/api/ivs/policies/" + policy.id() + "/publish", Map.of(),
                PolicyApi.PolicyView.class).getBody();
    }
}


