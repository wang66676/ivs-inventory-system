package com.ivs.app;

import com.ivs.procurement.payable.api.PayableApi;
import com.ivs.procurement.returns.api.PurchaseReturnApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M5：采购收货生成应付、付款核销、采购退货冲减应付。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PayableIntegrationTest {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() { TestDatabaseCleaner.clean(jdbcTemplate); TestAuthSupport.authenticate(restTemplate); }

    @Test
    void 收货生成应付_付款核销_退货冲减应付() {
        UUID supplierId = post("/api/ivs/suppliers", Map.of("name", "应付供应商", "supplierType", "OTHER"),
                com.ivs.masterdata.api.MasterdataApi.SupplierView.class).id();
        var category = post("/api/ivs/product-categories", Map.of("code", "APC-" + UUID.randomUUID().toString().substring(0, 6),
                "name", "应付配件", "trackingMode", "QUANTITY", "statusTemplate", "SIMPLE", "unit", "件", "sortOrder", 0),
                com.ivs.masterdata.api.MasterdataApi.CategoryView.class);
        UUID productId = post("/api/ivs/products", Map.of("name", "应付测试件", "categoryId", category.id().toString(),
                "unit", "件", "minPrice", 0), com.ivs.masterdata.api.MasterdataApi.ProductView.class).id();
        UUID warehouseId = post("/api/ivs/warehouses", Map.of("code", "APWH-" + UUID.randomUUID().toString().substring(0, 6),
                "name", "应付仓", "type", "MAIN"), com.ivs.masterdata.api.MasterdataApi.WarehouseView.class).id();
        var order = post("/api/ivs/purchase-orders", Map.of("supplierId", supplierId.toString(),
                "warehouseId", warehouseId.toString(), "lines", List.of(Map.of("productId", productId.toString(),
                        "quantity", 10, "unitPrice", 100, "taxRate", new BigDecimal("0.13")))),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseOrderView.class);
        post("/api/ivs/purchase-orders/" + order.id() + "/receive", Map.of(
                "warehouseId", warehouseId.toString(), "lines", List.of(Map.of("orderLineId",
                        order.lines().get(0).id().toString(), "quantity", 10))),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseReceiptView.class);

        PayableApi.PayableView[] payables = restTemplate.getForEntity(
                "/api/ivs/payables?supplierId=" + supplierId, PayableApi.PayableView[].class).getBody();
        assertThat(payables).isNotNull().hasSize(1);
        assertThat(payables[0].amount()).isEqualByComparingTo("1000.00");
        assertThat(payables[0].purchaseQuantity()).isEqualByComparingTo("10.000");
        assertThat(payables[0].outstanding()).isEqualByComparingTo("1000.00");

        PayableApi.PayableView paid = post("/api/ivs/payables/" + payables[0].id() + "/pay",
                Map.of("amount", 300, "paymentDate", "2026-11-12"), PayableApi.PayableView.class);
        assertThat(paid.outstanding()).isEqualByComparingTo("700.00");
        assertThat(paid.status().name()).isEqualTo("PARTIALLY_PAID");
        assertThat(paid.lastPaymentDate()).isEqualTo(java.time.LocalDate.of(2026, 11, 12));

        PurchaseReturnApi.ReturnView ret = post("/api/ivs/purchase-returns", Map.of(
                "supplierId", supplierId.toString(), "warehouseId", warehouseId.toString(),
                "reason", "退货冲应付", "lines", List.of(Map.of("productId", productId.toString(),
                        "quantity", 2, "unitPrice", 100, "batchNo", "-"))), PurchaseReturnApi.ReturnView.class);
        post("/api/ivs/purchase-returns/" + ret.id() + "/post", Map.of(), PurchaseReturnApi.ReturnView.class);
        PayableApi.PayableView[] after = restTemplate.getForEntity(
                "/api/ivs/payables?supplierId=" + supplierId, PayableApi.PayableView[].class).getBody();
        assertThat(after).isNotNull().hasSize(1);
        assertThat(after[0].amount()).isEqualByComparingTo("800.00");
        assertThat(after[0].outstanding()).isEqualByComparingTo("500.00");
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}
