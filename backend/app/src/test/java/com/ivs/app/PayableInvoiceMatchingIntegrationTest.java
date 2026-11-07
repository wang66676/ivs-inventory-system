package com.ivs.app;

import com.ivs.app.orchestration.InvoicePayableMatchService;
import com.ivs.billing.purchase.api.PurchaseInvoiceApi;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.procurement.payable.api.PayableApi;
import com.ivs.procurement.purchase.api.ProcurementApi;
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

import static org.assertj.core.api.Assertions.assertThat;

/** 蓝字进项发票按供应商自动匹配应付，优先匹配到期最早应付。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PayableInvoiceMatchingIntegrationTest {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 蓝字进项自动匹配多张应付并回写已开票未开票金额() {
        MasterdataApi.SupplierView supplier = post("/api/ivs/suppliers", Map.of(
                "name", "进项匹配整车厂", "supplierType", SupplierType.OEM.name()), MasterdataApi.SupplierView.class);
        MasterdataApi.WarehouseView warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "MATCH-WH", "name", "匹配测试仓", "type", WarehouseType.MAIN.name()),
                MasterdataApi.WarehouseView.class);
        MasterdataApi.CategoryView category = post("/api/ivs/product-categories", Map.of(
                "code", "MATCH-PART", "name", "匹配配件", "trackingMode", TrackingMode.QUANTITY.name(),
                "statusTemplate", "SIMPLE", "unit", "件", "sortOrder", 0), MasterdataApi.CategoryView.class);
        MasterdataApi.ProductView product = post("/api/ivs/products", Map.of(
                "name", "匹配测试配件", "categoryId", category.id().toString(), "unit", "件", "minPrice", 0),
                MasterdataApi.ProductView.class);

        receive(supplier.id(), warehouse.id(), product.id(), 10, 2000);
        receive(supplier.id(), warehouse.id(), product.id(), 5, 3000);

        PurchaseInvoiceApi.PurchaseInvoiceView invoice = post("/api/ivs/purchase-invoices/blue", Map.of(
                "supplierId", supplier.id().toString(), "amount", 30000, "taxRate", 0.13,
                "externalInvoiceNo", "BLUE-2026-001"), PurchaseInvoiceApi.PurchaseInvoiceView.class);
        assertThat(invoice.matchedAmount()).isEqualByComparingTo("0.00");
        assertThat(invoice.unmatchedAmount()).isEqualByComparingTo("30000.00");

        InvoicePayableMatchService.MatchResult result = post(
                "/api/ivs/purchase-invoice-matches/" + invoice.id() + "/auto", Map.of(),
                InvoicePayableMatchService.MatchResult.class);
        assertThat(result.lines()).hasSize(2);
        assertThat(result.matchedNow()).isEqualByComparingTo("30000.00");
        assertThat(result.unmatchedAmount()).isEqualByComparingTo("0.00");

        PayableApi.PayableView[] payables = restTemplate.getForEntity(
                "/api/ivs/payables", PayableApi.PayableView[].class).getBody();
        assertThat(payables).isNotNull().hasSize(2);
        assertThat(payables).extracting(PayableApi.PayableView::invoiceMatchedAmount)
                .containsExactlyInAnyOrder(new BigDecimal("20000.00"), new BigDecimal("10000.00"));
        assertThat(payables).extracting(PayableApi.PayableView::uninvoicedAmount)
                .containsExactlyInAnyOrder(new BigDecimal("0.00"), new BigDecimal("5000.00"));
    }

    private void receive(java.util.UUID supplierId, java.util.UUID warehouseId, java.util.UUID productId,
                         int quantity, int unitPrice) {
        ProcurementApi.PurchaseOrderView order = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplierId.toString(), "warehouseId", warehouseId.toString(),
                "lines", List.of(Map.of("productId", productId.toString(), "quantity", quantity,
                        "unitPrice", unitPrice))), ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        post("/api/ivs/purchase-orders/" + order.id() + "/receive", Map.of(
                "warehouseId", warehouseId.toString(),
                "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(), "quantity", quantity))),
                ProcurementApi.PurchaseReceiptView.class);
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}
