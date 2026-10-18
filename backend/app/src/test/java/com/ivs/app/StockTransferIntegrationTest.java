package com.ivs.app;

import com.ivs.inventory.stock.api.InventoryApi;
import com.ivs.inventory.stock.StockMoveType;
import com.ivs.inventory.transfer.api.StockTransferApi;
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

/** M5：库存调拨审核发出、在途、签收差异与可用库存校验。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class StockTransferIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 调拨审核发出后在途签收并登记差异() {
        Fixture fixture = fixture();

        StockTransferApi.TransferView transfer = post("/api/ivs/transfers", Map.of(
                "fromWarehouseId", fixture.fromWarehouseId().toString(),
                "toWarehouseId", fixture.toWarehouseId().toString(), "remark", "门店补货",
                "lines", List.of(Map.of("productId", fixture.productId().toString(), "batchNo", "-", "quantity", 4))),
                StockTransferApi.TransferView.class);
        assertThat(transfer.transferNo()).startsWith("TR-");
        assertThat(transfer.status().name()).isEqualTo("DRAFT");
        transfer = post("/api/ivs/transfers/" + transfer.id() + "/approve", Map.of(),
                StockTransferApi.TransferView.class);
        assertThat(transfer.status().name()).isEqualTo("APPROVED");

        transfer = post("/api/ivs/transfers/" + transfer.id() + "/dispatch", Map.of(),
                StockTransferApi.TransferView.class);
        assertThat(transfer.status().name()).isEqualTo("IN_TRANSIT");
        assertThat(balance(fixture.fromWarehouseId(), fixture.productId())).isEqualTo("6.000");

        Map<String, Object> receiveBody = Map.of("lines", List.of(Map.of(
                "lineId", transfer.lines().get(0).id().toString(),
                "receivedQuantity", 3, "differenceReason", "运输破损")));
        StockTransferApi.TransferView received = post("/api/ivs/transfers/" + transfer.id() + "/receive",
                receiveBody, StockTransferApi.TransferView.class);
        assertThat(received.status().name()).isEqualTo("RECEIVED");
        assertThat(received.differenceQuantity()).isEqualByComparingTo("1.000");
        assertThat(received.lines().get(0).differenceReason()).isEqualTo("运输破损");
        assertThat(balance(fixture.toWarehouseId(), fixture.productId())).isEqualTo("3.000");

        InventoryApi.LedgerView[] ledger = restTemplate.getForEntity(
                "/api/ivs/inventory/ledger?productId=" + fixture.productId(),
                InventoryApi.LedgerView[].class).getBody();
        assertThat(ledger).isNotNull();
        assertThat(java.util.Arrays.stream(ledger).filter(row -> row.moveType() == StockMoveType.TRANSFER_OUT)
                .count()).isEqualTo(1);
        assertThat(java.util.Arrays.stream(ledger).filter(row -> row.moveType() == StockMoveType.TRANSFER_IN)
                .count()).isEqualTo(1);
    }

    @Test
    void 可用库存不足时拒绝发出且已审核调拨可取消() {
        Fixture fixture = fixture();
        StockTransferApi.TransferView transfer = post("/api/ivs/transfers", Map.of(
                "fromWarehouseId", fixture.fromWarehouseId().toString(),
                "toWarehouseId", fixture.toWarehouseId().toString(),
                "lines", List.of(Map.of("productId", fixture.productId().toString(), "batchNo", "-", "quantity", 20))),
                StockTransferApi.TransferView.class);
        transfer = post("/api/ivs/transfers/" + transfer.id() + "/approve", Map.of(),
                StockTransferApi.TransferView.class);

        var response = restTemplate.postForEntity("/api/ivs/transfers/" + transfer.id() + "/dispatch",
                Map.of(), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("STOCK_NOT_AVAILABLE");

        StockTransferApi.TransferView cancelled = post("/api/ivs/transfers/" + transfer.id() + "/cancel",
                Map.of(), StockTransferApi.TransferView.class);
        assertThat(cancelled.status().name()).isEqualTo("CANCELLED");
        assertThat(balance(fixture.fromWarehouseId(), fixture.productId())).isEqualTo("10.000");
    }

    private String balance(UUID warehouseId, UUID productId) {
        InventoryApi.BalanceView[] rows = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + warehouseId,
                InventoryApi.BalanceView[].class).getBody();
        assertThat(rows).isNotNull();
        return java.util.Arrays.stream(rows).filter(row -> row.productId().equals(productId))
                .findFirst().orElseThrow().onHandQty().toPlainString();
    }

    private Fixture fixture() {
        MasterdataApi.CategoryView category = post("/api/ivs/product-categories", Map.of(
                "code", "PART-" + UUID.randomUUID().toString().substring(0, 6), "name", "配件",
                "trackingMode", TrackingMode.QUANTITY.name(), "statusTemplate", "SIMPLE",
                "unit", "件", "sortOrder", 0), MasterdataApi.CategoryView.class);
        MasterdataApi.ProductView product = post("/api/ivs/products", Map.of(
                "name", "调拨测试件", "categoryId", category.id().toString(), "unit", "件", "minPrice", 0),
                MasterdataApi.ProductView.class);
        MasterdataApi.WarehouseView from = post("/api/ivs/warehouses", Map.of(
                "code", "WH-" + UUID.randomUUID().toString().substring(0, 6), "name", "调出仓",
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
        MasterdataApi.WarehouseView to = post("/api/ivs/warehouses", Map.of(
                "code", "WH-" + UUID.randomUUID().toString().substring(0, 6), "name", "调入仓",
                "type", WarehouseType.SHOP.name()), MasterdataApi.WarehouseView.class);
        MasterdataApi.SupplierView supplier = post("/api/ivs/suppliers", Map.of(
                "name", "调拨供应商", "supplierType", SupplierType.OTHER.name()), MasterdataApi.SupplierView.class);

        ProcurementApi.PurchaseOrderView order = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.id().toString(), "warehouseId", from.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 10,
                        "unitPrice", 20, "taxRate", new BigDecimal("0.13")))), ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        post("/api/ivs/purchase-orders/" + order.id() + "/receive", Map.of(
                "warehouseId", from.id().toString(),
                "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(), "quantity", 10))),
                ProcurementApi.PurchaseReceiptView.class);
        return new Fixture(product.id(), from.id(), to.id());
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }

    private record Fixture(UUID productId, UUID fromWarehouseId, UUID toWarehouseId) {
    }
}

