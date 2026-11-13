package com.ivs.app;

import com.ivs.billing.purchase.api.PurchaseInvoiceApi;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 进项红字发票 → 自动入池 → 入库单抵扣车款 的闭环。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PurchaseInvoiceRebateIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 蓝字进项发票保存采购订单和多VIN联动信息() {
        UUID supplier = post("/api/ivs/suppliers", Map.of("name", "蓝票整车厂", "supplierType", SupplierType.OEM.name()),
                MasterdataApi.SupplierView.class).id();
        UUID orderId = UUID.randomUUID();
        PurchaseInvoiceApi.PurchaseInvoiceView blue = restTemplate.postForEntity(
                "/api/ivs/purchase-invoices/blue", Map.of(
                        "supplierId", supplier.toString(), "purchaseOrderId", orderId.toString(),
                        "serialNos", List.of("LZZBLUE000000001", "LZZBLUE000000002"),
                        "amount", 820000, "taxRate", 0.13, "externalInvoiceNo", "B-2026-001"),
                PurchaseInvoiceApi.PurchaseInvoiceView.class).getBody();
        assertThat(blue).isNotNull();
        assertThat(blue.purchaseOrderId()).isEqualTo(orderId);
        assertThat(blue.serialNos()).containsExactly("LZZBLUE000000001", "LZZBLUE000000002");
        assertThat(blue.amount()).isEqualByComparingTo("820000.00");
    }

    @Test
    void 厂家红字发票登记后自动入池并可抵扣车款() {
        UUID supplier = post("/api/ivs/suppliers", Map.of("name", "某整车厂", "supplierType", SupplierType.OEM.name()),
                MasterdataApi.SupplierView.class).id();
        UUID warehouse = post("/api/ivs/warehouses", Map.of("code", "WH-R1", "name", "整车仓",
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class).id();
        MasterdataApi.CategoryView category = post("/api/ivs/product-categories", Map.of(
                "code", "PART", "name", "配件", "trackingMode", TrackingMode.QUANTITY.name(),
                "statusTemplate", "SIMPLE", "unit", "件", "sortOrder", 0), MasterdataApi.CategoryView.class);
        UUID product = post("/api/ivs/products", Map.of("name", "配件", "categoryId", category.id().toString(),
                "unit", "件", "minPrice", 0), MasterdataApi.ProductView.class).id();

        // 1) 登记厂家红字发票 30,000（税率 13%）
        PurchaseInvoiceApi.PurchaseInvoiceView red = restTemplate.postForEntity("/api/ivs/purchase-invoices/red",
                Map.of("supplierId", supplier.toString(), "amount", 30000, "taxRate", 0.13,
                        "externalInvoiceNo", "R-2026-001"), PurchaseInvoiceApi.PurchaseInvoiceView.class).getBody();
        assertThat(red).isNotNull();
        assertThat(red.invoiceNo()).startsWith("PINV-R-");
        assertThat(red.taxAmount()).isEqualByComparingTo("3451.33");

        // 2) 自动入池：该厂家余额 30,000
        RebateFundApi.PoolView pool = restTemplate.getForEntity("/api/ivs/rebate-fund-pool",
                RebateFundApi.PoolView.class).getBody();
        assertThat(pool).isNotNull();
        assertThat(pool.balanceAmount()).isEqualByComparingTo("30000.00");
        RebateFundApi.TxnView[] txns = restTemplate.getForEntity(
                "/api/ivs/rebate-fund-pool/txns?supplierId=" + supplier, RebateFundApi.TxnView[].class).getBody();
        assertThat(txns).hasSize(1);
        assertThat(txns[0].sourceType()).isEqualTo("RED_INVOICE");
        assertThat(txns[0].sourceNo()).isEqualTo(red.invoiceNo());

        // 3) 入库单确认时抵扣：采购 10 × 2,000 = 20,000
        ProcurementApi.PurchaseOrderView order = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.toString(), "warehouseId", warehouse.toString(),
                "lines", List.of(Map.of("productId", product.toString(), "quantity", 10, "unitPrice", 2000))),
                ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        Map<String, Object> receiveBody = new HashMap<>();
        receiveBody.put("warehouseId", warehouse.toString());
        receiveBody.put("lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(), "quantity", 10)));
        receiveBody.put("offsetTxnIds", List.of(txns[0].id().toString()));
        ProcurementApi.PurchaseReceiptView receipt = restTemplate.postForEntity(
                "/api/ivs/purchase-orders/" + order.id() + "/receive", receiveBody,
                ProcurementApi.PurchaseReceiptView.class).getBody();
        assertThat(receipt).isNotNull();
        assertThat(receipt.amount()).isEqualByComparingTo("20000.00");
        assertThat(receipt.offsetAmount()).isEqualByComparingTo("20000.00");
        assertThat(receipt.netAmount()).isEqualByComparingTo("0.00");

        // 4) 池余额剩 10,000
        RebateFundApi.PoolView after = restTemplate.getForEntity("/api/ivs/rebate-fund-pool",
                RebateFundApi.PoolView.class).getBody();
        assertThat(after).isNotNull();
        assertThat(after.balanceAmount()).isEqualByComparingTo("10000.00");
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}