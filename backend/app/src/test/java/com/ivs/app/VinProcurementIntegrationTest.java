package com.ivs.app;

import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.procurement.purchase.api.ProcurementApi;
import com.ivs.inventory.stock.api.InventoryApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 采购收货时直接录入 VIN 与单车采购价，系统自动建立 VIN 档案。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VinProcurementIntegrationTest {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() { TestDatabaseCleaner.clean(jdbcTemplate); TestAuthSupport.authenticate(restTemplate); }

    @Test
    void 收货时自动建立VIN档案并按每台采购价入账() {
        var supplier = post("/api/ivs/suppliers", Map.of("name", "VIN整车厂", "supplierType", SupplierType.OEM.name()),
                MasterdataApi.SupplierView.class);
        var warehouse = post("/api/ivs/warehouses", Map.of("code", "VINWH", "name", "VIN整车仓",
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
        var secondWarehouse = post("/api/ivs/warehouses", Map.of("code", "VINWH2", "name", "VIN分仓",
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
        var category = post("/api/ivs/product-categories", Map.of("code", "VINCAT", "name", "VIN整车",
                "trackingMode", TrackingMode.SERIAL.name(), "statusTemplate", "VEHICLE", "unit", "台", "sortOrder", 0),
                MasterdataApi.CategoryView.class);
        var product = post("/api/ivs/products", Map.of("name", "VIN测试车", "categoryId", category.id().toString(),
                "unit", "台", "minPrice", 0), MasterdataApi.ProductView.class);
        var order = post("/api/ivs/purchase-orders", Map.of("supplierId", supplier.id().toString(),
                "warehouseId", warehouse.id().toString(), "lines", List.of(Map.of("productId", product.id().toString(),
                        "quantity", 2, "unitPrice", 400000, "taxRate", new BigDecimal("0.13")))),
                ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(), ProcurementApi.PurchaseOrderView.class);
        UUID lineId = order.lines().get(0).id();
        Map<String, Object> body = new HashMap<>();
        body.put("warehouseId", warehouse.id().toString());
        body.put("lines", List.of(
                Map.of("orderLineId", lineId.toString(), "quantity", 1,
                        "serialNos", List.of("LZZVIN2026000001"), "unitPrice", 410000,
                        "warehouseId", warehouse.id().toString(),
                        "certificateApplicationDate", LocalDate.of(2026, 10, 20).toString()),
                Map.of("orderLineId", lineId.toString(), "quantity", 1,
                        "serialNos", List.of("LZZVIN2026000002"), "unitPrice", 430000,
                        "warehouseId", secondWarehouse.id().toString(),
                        "certificateApplicationDate", LocalDate.of(2026, 10, 21).toString())
        ));
        var receipt = post("/api/ivs/purchase-orders/" + order.id() + "/receive", body,
                ProcurementApi.PurchaseReceiptView.class);
        assertThat(receipt.amount()).isEqualByComparingTo("840000.00");
        assertThat(receipt.lines()).hasSize(2);
        assertThat(receipt.lines()).anySatisfy(line -> {
            assertThat(line.serialNos()).containsExactly("LZZVIN2026000001");
            assertThat(line.warehouseId()).isEqualTo(warehouse.id());
            assertThat(line.certificateApplicationDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        });
        assertThat(receipt.lines()).anySatisfy(line -> {
            assertThat(line.serialNos()).containsExactly("LZZVIN2026000002");
            assertThat(line.warehouseId()).isEqualTo(secondWarehouse.id());
            assertThat(line.certificateApplicationDate()).isEqualTo(LocalDate.of(2026, 10, 21));
        });

        MasterdataApi.SerialView[] serials = restTemplate.getForEntity(
                "/api/ivs/product-serials?keyword=LZZVIN202600000", MasterdataApi.SerialView[].class).getBody();
        assertThat(serials).isNotNull().hasSize(2);
        assertThat(serials).allSatisfy(row -> assertThat(row.status()).isEqualTo(SerialStatus.IN_STOCK));
        Map<String, BigDecimal> prices = new HashMap<>();
        Map<String, MasterdataApi.SerialView> serialByNo = new HashMap<>();
        for (var serial : serials) {
            prices.put(serial.serialNo(), serial.purchasePrice());
            serialByNo.put(serial.serialNo(), serial);
        }
        assertThat(prices.get("LZZVIN2026000001")).isEqualByComparingTo("410000.00");
        assertThat(prices.get("LZZVIN2026000002")).isEqualByComparingTo("430000.00");
        assertThat(serialByNo.get("LZZVIN2026000001").warehouseId()).isEqualTo(warehouse.id());
        assertThat(serialByNo.get("LZZVIN2026000001").certificateApplicationDate())
                .isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(serialByNo.get("LZZVIN2026000002").warehouseId()).isEqualTo(secondWarehouse.id());
        assertThat(serialByNo.get("LZZVIN2026000002").certificateApplicationDate())
                .isEqualTo(LocalDate.of(2026, 10, 21));

        InventoryApi.BalanceView[] balances = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + warehouse.id(), InventoryApi.BalanceView[].class).getBody();
        assertThat(balances).isNotNull().hasSize(1);
        assertThat(balances[0].amount()).isEqualByComparingTo("410000.00");
        assertThat(balances[0].purchaseOrderId()).isEqualTo(order.id());
        assertThat(balances[0].purchaseOrderNo()).isEqualTo(order.externalOrderNo() == null
                ? order.orderNo() : order.externalOrderNo());
        InventoryApi.BalanceView[] secondBalances = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + secondWarehouse.id(),
                InventoryApi.BalanceView[].class).getBody();
        assertThat(secondBalances).isNotNull().hasSize(1);
        assertThat(secondBalances[0].amount()).isEqualByComparingTo("430000.00");
        assertThat(secondBalances[0].purchaseOrderId()).isEqualTo(order.id());
    }

    @Test
    void 同一订单可无默认仓库并按VIN分仓且后补合格证日期() {
        var supplier = post("/api/ivs/suppliers", Map.of("name", "分仓整车厂", "supplierType", SupplierType.OEM.name()),
                MasterdataApi.SupplierView.class);
        var firstWarehouse = post("/api/ivs/warehouses", Map.of("code", "VIN-SPLIT-A", "name", "分仓A",
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
        var secondWarehouse = post("/api/ivs/warehouses", Map.of("code", "VIN-SPLIT-B", "name", "分仓B",
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
        var thirdWarehouse = post("/api/ivs/warehouses", Map.of("code", "VIN-SPLIT-C", "name", "分仓C",
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
        var category = post("/api/ivs/product-categories", Map.of("code", "VIN-SPLIT-CAT", "name", "分仓整车",
                "trackingMode", TrackingMode.SERIAL.name(), "statusTemplate", "VEHICLE", "unit", "台", "sortOrder", 0),
                MasterdataApi.CategoryView.class);
        var richProduct = post("/api/ivs/products", Map.of("name", "Q3牵引车-财富配置", "series", "Q3",
                "model", "6x4", "categoryId", category.id().toString(), "unit", "台", "minPrice", 0),
                MasterdataApi.ProductView.class);
        var comfortProduct = post("/api/ivs/products", Map.of("name", "Q3牵引车-舒享配置", "series", "Q3",
                "model", "6x4 PRO", "categoryId", category.id().toString(), "unit", "台", "minPrice", 0),
                MasterdataApi.ProductView.class);
        var order = post("/api/ivs/purchase-orders", Map.of("supplierId", supplier.id().toString(),
                "externalOrderNo", "SPLIT-WH-001", "lines", List.of(
                        Map.of("productId", richProduct.id().toString(), "quantity", 1, "unitPrice", 410000,
                                "configText", "标载财富版"),
                        Map.of("productId", comfortProduct.id().toString(), "quantity", 1, "unitPrice", 430000,
                                "configText", "复合舒享版"))),
                ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);

        var receipt = post("/api/ivs/purchase-orders/" + order.id() + "/receive", Map.of(
                "lines", List.of(
                        Map.of("orderLineId", order.lines().get(0).id().toString(), "serialNos",
                                List.of("LZZSPLITWH0000001"), "unitPrice", 410000,
                                "warehouseId", firstWarehouse.id().toString()),
                        Map.of("orderLineId", order.lines().get(1).id().toString(), "serialNos",
                                List.of("LZZSPLITWH0000002"), "unitPrice", 430000,
                                "warehouseId", secondWarehouse.id().toString(),
                                "certificateApplicationDate", "2026-10-22")
                )), ProcurementApi.PurchaseReceiptView.class);
        assertThat(receipt.lines()).hasSize(2);
        assertThat(receipt.lines()).anySatisfy(line -> {
            assertThat(line.serialNos()).containsExactly("LZZSPLITWH0000001");
            assertThat(line.configText()).isEqualTo("标载财富版");
            assertThat(line.cabType()).isEqualTo("平顶");
            assertThat(line.warehouseId()).isEqualTo(firstWarehouse.id());
            assertThat(line.certificateApplicationDate()).isNull();
        });
        assertThat(receipt.lines()).anySatisfy(line -> {
            assertThat(line.serialNos()).containsExactly("LZZSPLITWH0000002");
            assertThat(line.configText()).isEqualTo("复合舒享版");
            assertThat(line.cabType()).isEqualTo("高顶");
            assertThat(line.warehouseId()).isEqualTo(secondWarehouse.id());
            assertThat(line.certificateApplicationDate()).isEqualTo(LocalDate.of(2026, 10, 22));
        });

        UUID firstLineId = receipt.lines().stream()
                .filter(line -> line.serialNos().contains("LZZSPLITWH0000001"))
                .findFirst().orElseThrow().id();
        var updated = restTemplate.exchange(
                "/api/ivs/purchase-receipts/" + receipt.id() + "/lines/" + firstLineId
                        + "/certificate-application-date",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("certificateApplicationDate", "2026-10-25")),
                ProcurementApi.PurchaseReceiptView.class).getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.lines()).anySatisfy(line -> {
            if (line.serialNos().contains("LZZSPLITWH0000001")) {
                assertThat(line.certificateApplicationDate()).isEqualTo(LocalDate.of(2026, 10, 25));
            }
        });
        MasterdataApi.SerialView[] serials = restTemplate.getForEntity(
                "/api/ivs/product-serials?keyword=LZZSPLITWH0000001", MasterdataApi.SerialView[].class).getBody();
        assertThat(serials).isNotNull().hasSize(1);
        assertThat(serials[0].certificateApplicationDate()).isEqualTo(LocalDate.of(2026, 10, 25));

        var moved = restTemplate.exchange(
                "/api/ivs/purchase-receipts/" + receipt.id() + "/lines/" + firstLineId + "/warehouse",
                org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("warehouseId", thirdWarehouse.id().toString())),
                ProcurementApi.PurchaseReceiptView.class).getBody();
        assertThat(moved).isNotNull();
        assertThat(moved.lines()).anySatisfy(line -> {
            if (line.serialNos().contains("LZZSPLITWH0000001")) {
                assertThat(line.warehouseId()).isEqualTo(thirdWarehouse.id());
            }
        });
        MasterdataApi.SerialView[] movedSerial = restTemplate.getForEntity(
                "/api/ivs/product-serials?keyword=LZZSPLITWH0000001", MasterdataApi.SerialView[].class).getBody();
        assertThat(movedSerial).isNotNull().hasSize(1);
        assertThat(movedSerial[0].warehouseId()).isEqualTo(thirdWarehouse.id());
    }

    @Test
    void 已入库VIN可整单退回并修正后重新收货() {
        var supplier = post("/api/ivs/suppliers", Map.of("name", "VIN退回整车厂", "supplierType", SupplierType.OEM.name()),
                MasterdataApi.SupplierView.class);
        var warehouse = post("/api/ivs/warehouses", Map.of("code", "VIN-RET-WH", "name", "退回测试仓",
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
        var category = post("/api/ivs/product-categories", Map.of("code", "VIN-RET-CAT", "name", "退回测试车",
                "trackingMode", TrackingMode.SERIAL.name(), "statusTemplate", "VEHICLE", "unit", "台", "sortOrder", 0),
                MasterdataApi.CategoryView.class);
        var product = post("/api/ivs/products", Map.of("name", "退回测试车", "categoryId", category.id().toString(),
                "unit", "台", "minPrice", 0), MasterdataApi.ProductView.class);
        var order = post("/api/ivs/purchase-orders", Map.of("supplierId", supplier.id().toString(),
                "externalOrderNo", "VIN-RETURN-ORDER-001", "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1, "unitPrice", 400000))),
                ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        UUID lineId = order.lines().get(0).id();
        Map<String, Object> receiveBody = Map.of("warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", lineId.toString(), "serialNos",
                        List.of("LZZRETURN00000001"), "unitPrice", 400000,
                        "warehouseId", warehouse.id().toString())));
        var receipt = post("/api/ivs/purchase-orders/" + order.id() + "/receive", receiveBody,
                ProcurementApi.PurchaseReceiptView.class);
        assertThat(receipt.status().name()).isEqualTo("POSTED");

        var returned = post("/api/ivs/purchase-receipts/" + receipt.id() + "/return",
                Map.of("reason", "VIN录入错误"), ProcurementApi.PurchaseReceiptView.class);
        assertThat(returned.status().name()).isEqualTo("RETURNED");
        var restoredOrder = restTemplate.getForEntity("/api/ivs/purchase-orders/" + order.id(),
                ProcurementApi.PurchaseOrderView.class).getBody();
        assertThat(restoredOrder.status().name()).isEqualTo("APPROVED");
        assertThat(restoredOrder.lines().get(0).receivedQuantity()).isEqualByComparingTo("0.000");
        var payables = restTemplate.getForEntity("/api/ivs/payables?supplierId=" + supplier.id(),
                Map[].class).getBody();
        assertThat(payables).isEmpty();

        var reReceived = post("/api/ivs/purchase-orders/" + order.id() + "/receive", receiveBody,
                ProcurementApi.PurchaseReceiptView.class);
        assertThat(reReceived.status().name()).isEqualTo("POSTED");
        assertThat(reReceived.lines().get(0).serialNos()).containsExactly("LZZRETURN00000001");
    }

    private <T> T post(String url, Object body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}

