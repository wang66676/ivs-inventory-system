package com.ivs.app;

import com.ivs.inventory.stock.api.InventoryApi;
import com.ivs.inventory.stock.StockMoveType;
import com.ivs.procurement.returns.api.PurchaseReturnApi;
import com.ivs.sales.order.api.SalesApi;
import com.ivs.sales.returns.api.SalesReturnApi;
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

/** M5：采购退货出库与销售退货回库、应收冲减。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReturnsIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 采购退货过账扣减库存并记录退货金额() {
        Fixture fixture = fixture();
        PurchaseReturnApi.ReturnView value = post("/api/ivs/purchase-returns", Map.of(
                "supplierId", fixture.supplierId().toString(), "warehouseId", fixture.warehouseId().toString(),
                "reason", "质量退货", "remark", "退回厂家",
                "lines", List.of(Map.of("productId", fixture.productId().toString(), "quantity", 3,
                        "unitPrice", 20, "batchNo", "-"))), PurchaseReturnApi.ReturnView.class);
        assertThat(value.returnNo()).startsWith("PR-");
        assertThat(value.totalAmount()).isEqualByComparingTo("60.00");

        value = post("/api/ivs/purchase-returns/" + value.id() + "/post", Map.of(),
                PurchaseReturnApi.ReturnView.class);
        assertThat(value.status().name()).isEqualTo("POSTED");
        assertThat(balance(fixture.warehouseId(), fixture.productId())).isEqualTo("7.000");

        InventoryApi.LedgerView[] ledger = restTemplate.getForEntity(
                "/api/ivs/inventory/ledger?productId=" + fixture.productId(),
                InventoryApi.LedgerView[].class).getBody();
        assertThat(ledger).isNotNull();
        var rows = java.util.Arrays.stream(ledger)
                .filter(row -> row.moveType() == StockMoveType.PURCHASE_RETURN_OUT).toList();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).quantity()).isEqualByComparingTo("3.000");
        assertThat(rows.get(0).bizNo()).isEqualTo(value.returnNo());
    }

    @Test
    void 序列号商品采购退货恢复为已退回状态() {
        var category = post("/api/ivs/product-categories", Map.of(
                "code", "SER-" + UUID.randomUUID().toString().substring(0, 6), "name", "序列号退货",
                "trackingMode", "SERIAL", "statusTemplate", "VEHICLE", "unit", "台", "sortOrder", 0),
                com.ivs.masterdata.api.MasterdataApi.CategoryView.class);
        var product = post("/api/ivs/products", Map.of(
                "name", "序列号退货车", "categoryId", category.id().toString(), "unit", "台", "minPrice", 0),
                com.ivs.masterdata.api.MasterdataApi.ProductView.class);
        var warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "SWH-" + UUID.randomUUID().toString().substring(0, 6), "name", "序列号退货仓",
                "type", "MAIN"), com.ivs.masterdata.api.MasterdataApi.WarehouseView.class);
        var supplier = post("/api/ivs/suppliers", Map.of(
                "name", "序列号退货供应商", "supplierType", "OEM"),
                com.ivs.masterdata.api.MasterdataApi.SupplierView.class);
        String serialNo = "UATRT" + UUID.randomUUID().toString().replace("-", "").substring(0, 14).toUpperCase();
        var serial = post("/api/ivs/product-serials", Map.of(
                "serialNo", serialNo, "productId", product.id().toString()),
                com.ivs.masterdata.api.MasterdataApi.SerialView.class);
        var order = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.id().toString(), "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1,
                        "unitPrice", 200000, "taxRate", new BigDecimal("0.13")))),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseOrderView.class);
        post("/api/ivs/purchase-orders/" + order.id() + "/receive", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(),
                        "serialNos", List.of(serialNo)))),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseReceiptView.class);

        PurchaseReturnApi.ReturnView value = post("/api/ivs/purchase-returns", Map.of(
                "supplierId", supplier.id().toString(), "warehouseId", warehouse.id().toString(),
                "reason", "整车退回厂家",
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1,
                        "unitPrice", 200000, "batchNo", "-", "serialNos", List.of(serialNo)))),
                PurchaseReturnApi.ReturnView.class);
        value = post("/api/ivs/purchase-returns/" + value.id() + "/post", Map.of(),
                PurchaseReturnApi.ReturnView.class);
        assertThat(value.status().name()).isEqualTo("POSTED");

        var serials = restTemplate.getForEntity("/api/ivs/product-serials?keyword=" + serialNo,
                com.ivs.masterdata.api.MasterdataApi.SerialView[].class).getBody();
        assertThat(serials).isNotNull().hasSize(1);
        assertThat(serials[0].status().name()).isEqualTo("RETURNED");
        assertThat(balance(warehouse.id(), product.id())).isEqualTo("0.000");
    }
    @Test
    void 销售退货回库并冲减应收() {
        Fixture fixture = fixture();
        UUID customerId = post("/api/ivs/customers", Map.of(
                "name", "退货客户", "customerType", "INDIVIDUAL", "creditLimit", 100000),
                com.ivs.masterdata.api.MasterdataApi.CustomerView.class).id();

        SalesApi.SalesOrderView order = post("/api/ivs/sales-orders", Map.of(
                "customerId", customerId.toString(), "warehouseId", fixture.warehouseId().toString(),
                "lines", List.of(Map.of("productId", fixture.productId().toString(), "quantity", 4,
                        "unitPrice", 100))), SalesApi.SalesOrderView.class);
        order = post("/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(),
                SalesApi.SalesOrderView.class);
        SalesApi.SalesDeliveryView delivery = post("/api/ivs/sales-orders/" + order.id() + "/deliver", Map.of(
                "warehouseId", fixture.warehouseId().toString(),
                "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(), "quantity", 4))),
                SalesApi.SalesDeliveryView.class);
        assertThat(balance(fixture.warehouseId(), fixture.productId())).isEqualTo("6.000");

        SalesReturnApi.ReturnView value = post("/api/ivs/sales-returns", Map.of(
                "customerId", customerId.toString(), "deliveryId", delivery.id().toString(),
                "warehouseId", fixture.warehouseId().toString(), "reason", "客户退货",
                "lines", List.of(Map.of("productId", fixture.productId().toString(), "quantity", 1,
                        "unitPrice", 100, "batchNo", "-"))), SalesReturnApi.ReturnView.class);
        value = post("/api/ivs/sales-returns/" + value.id() + "/post", Map.of(),
                SalesReturnApi.ReturnView.class);
        assertThat(value.status().name()).isEqualTo("POSTED");
        assertThat(value.receivableAdjustmentAmount()).isEqualByComparingTo("100.00");
        assertThat(value.refundAmount()).isEqualByComparingTo("0.00");
        assertThat(balance(fixture.warehouseId(), fixture.productId())).isEqualTo("7.000");

        SalesApi.ReceivableView[] receivables = restTemplate.getForEntity(
                "/api/ivs/receivables?customerId=" + customerId,
                SalesApi.ReceivableView[].class).getBody();
        assertThat(receivables).isNotNull().hasSize(1);
        assertThat(receivables[0].amount()).isEqualByComparingTo("300.00");
    }

    @Test
    void 销售退货累计不能超过原出库金额() {
        Fixture fixture = fixture();
        UUID customerId = post("/api/ivs/customers", Map.of(
                "name", "超额退货客户", "customerType", "INDIVIDUAL", "creditLimit", 100000),
                com.ivs.masterdata.api.MasterdataApi.CustomerView.class).id();
        SalesApi.SalesOrderView order = post("/api/ivs/sales-orders", Map.of(
                "customerId", customerId.toString(), "warehouseId", fixture.warehouseId().toString(),
                "lines", List.of(Map.of("productId", fixture.productId().toString(), "quantity", 4,
                        "unitPrice", 100))), SalesApi.SalesOrderView.class);
        order = post("/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(),
                SalesApi.SalesOrderView.class);
        var delivery = post("/api/ivs/sales-orders/" + order.id() + "/deliver", Map.of(
                "warehouseId", fixture.warehouseId().toString(),
                "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(), "quantity", 4))),
                SalesApi.SalesDeliveryView.class);
        SalesReturnApi.ReturnView value = post("/api/ivs/sales-returns", Map.of(
                "customerId", customerId.toString(), "deliveryId", delivery.id().toString(),
                "warehouseId", fixture.warehouseId().toString(),
                "lines", List.of(Map.of("productId", fixture.productId().toString(), "quantity", 5,
                        "unitPrice", 100, "batchNo", "-"))), SalesReturnApi.ReturnView.class);
        var response = restTemplate.postForEntity("/api/ivs/sales-returns/" + value.id() + "/post",
                Map.of(), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("SALES_RETURN_EXCEEDS_DELIVERY");
    }

    private Fixture fixture() {
        var category = post("/api/ivs/product-categories", Map.of(
                "code", "RET-" + UUID.randomUUID().toString().substring(0, 6), "name", "退货配件",
                "trackingMode", "QUANTITY", "statusTemplate", "SIMPLE", "unit", "件", "sortOrder", 0),
                com.ivs.masterdata.api.MasterdataApi.CategoryView.class);
        var product = post("/api/ivs/products", Map.of(
                "name", "退货测试件", "categoryId", category.id().toString(), "unit", "件", "minPrice", 0),
                com.ivs.masterdata.api.MasterdataApi.ProductView.class);
        var warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "RWH-" + UUID.randomUUID().toString().substring(0, 6), "name", "退货仓",
                "type", "MAIN"), com.ivs.masterdata.api.MasterdataApi.WarehouseView.class);
        var supplier = post("/api/ivs/suppliers", Map.of(
                "name", "退货供应商", "supplierType", "OTHER"),
                com.ivs.masterdata.api.MasterdataApi.SupplierView.class);
        var order = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.id().toString(), "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 10,
                        "unitPrice", 20, "taxRate", new BigDecimal("0.13")))),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseOrderView.class);
        post("/api/ivs/purchase-orders/" + order.id() + "/receive", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(), "quantity", 10))),
                com.ivs.procurement.purchase.api.ProcurementApi.PurchaseReceiptView.class);
        return new Fixture(product.id(), warehouse.id(), supplier.id());
    }

    private String balance(UUID warehouseId, UUID productId) {
        InventoryApi.BalanceView[] rows = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + warehouseId,
                InventoryApi.BalanceView[].class).getBody();
        assertThat(rows).isNotNull();
        return java.util.Arrays.stream(rows).filter(row -> row.productId().equals(productId))
                .findFirst().orElseThrow().onHandQty().toPlainString();
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }

    private record Fixture(UUID productId, UUID warehouseId, UUID supplierId) {
    }
}

