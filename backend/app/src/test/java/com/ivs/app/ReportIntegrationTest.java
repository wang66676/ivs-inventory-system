package com.ivs.app;

import com.ivs.app.report.ReportApi;
import com.ivs.sales.order.api.SalesApi;
import com.ivs.sales.returns.api.SalesReturnApi;
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

/** M5：经营报表口径 —— 采购、销售、退货、库存资金与毛利。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReportIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 月报毛利与库存资金占用口径一致() {
        UUID supplierId = post("/api/ivs/suppliers", Map.of(
                "name", "报表供应商", "supplierType", "OTHER"),
                com.ivs.masterdata.api.MasterdataApi.SupplierView.class).id();
        var category = post("/api/ivs/product-categories", Map.of(
                "code", "REP-" + UUID.randomUUID().toString().substring(0, 6), "name", "报表配件",
                "trackingMode", "QUANTITY", "statusTemplate", "SIMPLE", "unit", "件", "sortOrder", 0),
                com.ivs.masterdata.api.MasterdataApi.CategoryView.class);
        var product = post("/api/ivs/products", Map.of(
                "name", "报表测试件", "categoryId", category.id().toString(), "unit", "件", "minPrice", 0),
                com.ivs.masterdata.api.MasterdataApi.ProductView.class);
        var warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "REPWH" + UUID.randomUUID().toString().substring(0, 6), "name", "报表仓",
                "type", "MAIN"), com.ivs.masterdata.api.MasterdataApi.WarehouseView.class);
        UUID customerId = post("/api/ivs/customers", Map.of(
                "name", "报表客户", "customerType", "INDIVIDUAL", "creditLimit", 100000),
                com.ivs.masterdata.api.MasterdataApi.CustomerView.class).id();

        var purchase = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplierId.toString(), "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 10,
                        "unitPrice", 20, "taxRate", new BigDecimal("0.13")))),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseOrderView.class);
        purchase = post("/api/ivs/purchase-orders/" + purchase.id() + "/approve", Map.of(),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseOrderView.class);
        post("/api/ivs/purchase-orders/" + purchase.id() + "/receive", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", purchase.lines().get(0).id().toString(), "quantity", 10))),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseReceiptView.class);

        SalesApi.SalesOrderView order = post("/api/ivs/sales-orders", Map.of(
                "customerId", customerId.toString(), "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 4, "unitPrice", 100))),
                SalesApi.SalesOrderView.class);
        order = post("/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(), SalesApi.SalesOrderView.class);
        order = post("/api/ivs/sales-orders/" + order.id() + "/lock", Map.of(), SalesApi.SalesOrderView.class);
        var delivery = post("/api/ivs/sales-orders/" + order.id() + "/deliver", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(), "quantity", 4))),
                SalesApi.SalesDeliveryView.class);
        SalesReturnApi.ReturnView salesReturn = post("/api/ivs/sales-returns", Map.of(
                "customerId", customerId.toString(), "deliveryId", delivery.id().toString(),
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1,
                        "unitPrice", 100, "batchNo", "-"))), SalesReturnApi.ReturnView.class);
        post("/api/ivs/sales-returns/" + salesReturn.id() + "/post", Map.of(), SalesReturnApi.ReturnView.class);

        String month = LocalDate.now().toString().substring(0, 7);
        ReportApi.MonthlyReport monthly = restTemplate.getForEntity(
                "/api/ivs/reports/monthly?from=" + month + "-01&to=" + LocalDate.now(),
                ReportApi.MonthlyReport.class).getBody();
        assertThat(monthly).isNotNull();
        ReportApi.MonthlyRow row = monthly.rows().stream().filter(item -> item.period().equals(month))
                .findFirst().orElseThrow();
        assertThat(row.purchaseAmount()).isEqualByComparingTo("200.00");
        assertThat(row.salesAmount()).isEqualByComparingTo("400.00");
        assertThat(row.salesReturnAmount()).isEqualByComparingTo("100.00");
        assertThat(row.costAmount()).isEqualByComparingTo("80.00");
        assertThat(row.salesReturnCostAmount()).isEqualByComparingTo("20.00");
        assertThat(row.grossProfit()).isEqualByComparingTo("240.00");
        assertThat(row.stockValue()).isEqualByComparingTo("140.00");

        ReportApi.GrossProfitReport gross = restTemplate.getForEntity(
                "/api/ivs/reports/gross-profit?from=" + month + "-01&to=" + LocalDate.now(),
                ReportApi.GrossProfitReport.class).getBody();
        assertThat(gross).isNotNull();
        ReportApi.GrossProfitRow profit = gross.rows().stream()
                .filter(item -> item.productId().equals(product.id())).findFirst().orElseThrow();
        assertThat(profit.netSalesAmount()).isEqualByComparingTo("300.00");
        assertThat(profit.netCostAmount()).isEqualByComparingTo("60.00");
        assertThat(profit.grossProfit()).isEqualByComparingTo("240.00");
        assertThat(profit.grossMargin()).isEqualByComparingTo("80.00");

        ReportApi.StockFundReport funds = restTemplate.getForEntity(
                "/api/ivs/reports/stock-funds?warehouseId=" + warehouse.id(),
                ReportApi.StockFundReport.class).getBody();
        assertThat(funds).isNotNull();
        assertThat(funds.totalAmount()).isEqualByComparingTo("140.00");
        assertThat(funds.totalOnHandQty()).isEqualByComparingTo("7.000");
        assertThat(funds.rows()).hasSize(1);
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}
