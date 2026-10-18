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
        assertThat(line.quantity()).isEqualByComparingTo("2.000");
        assertThat(line.unitPrice()).isEqualByComparingTo("410000.00");
        assertThat(line.amount()).isEqualByComparingTo("820000.00");
        assertThat(line.policies()).extracting(ProcurementApi.PolicyRefView::name)
                .containsExactly("年度返利", "库存车补贴");

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


