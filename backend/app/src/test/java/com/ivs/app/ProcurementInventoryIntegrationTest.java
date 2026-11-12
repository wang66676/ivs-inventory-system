package com.ivs.app;

import com.ivs.inventory.stock.api.InventoryApi;
import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.procurement.purchase.PurchaseOrderStatus;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M2 采购与库存验收：按序列号收货落台账与余额、数量商品移动加权成本、
 * 分批收货状态推进、收货校验、锁货与超时释放、台账余额一致性与看板。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProcurementInventoryIntegrationTest {

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
    void 整车采购按序列号收货并写入库存台账与余额() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE", "整车", TrackingMode.SERIAL, "VEHICLE", "台");
        MasterdataApi.ProductView product = createProduct("Q3 牵引车", vehicle.id(), "台", "某厂", "Q3", "T31");
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-01", "主仓");
        MasterdataApi.SupplierView oem = createSupplier("某整车厂", SupplierType.OEM);

        // 采购前先建档（序列号档案由 M1 负责，采购收货只做状态流转与入库）
        MasterdataApi.SerialView serial1 = createSerial("LZZ1CLWB0NA100001", product.id());
        MasterdataApi.SerialView serial2 = createSerial("LZZ1CLWB0NA100002", product.id());

        ProcurementApi.PurchaseOrderView order = createOrder(oem.id(), warehouse.id(),
                List.of(new ProcurementApi.SavePurchaseOrderLineCommand(product.id(), new BigDecimal("2"),
                        new BigDecimal("420000"), new BigDecimal("0.13"), null)));
        assertThat(order.status()).isEqualTo(PurchaseOrderStatus.DRAFT);
        order = restTemplate.postForEntity("/api/ivs/purchase-orders/" + order.id() + "/approve", null,
                ProcurementApi.PurchaseOrderView.class).getBody();
        assertThat(order.status()).isEqualTo(PurchaseOrderStatus.APPROVED);

        List<Map<String, Object>> receiveLines = new ArrayList<>();
        receiveLines.add(Map.of("orderLineId", order.lines().get(0).id().toString(),
                "serialNos", List.of("LZZ1CLWB0NA100001", "LZZ1CLWB0NA100002")));
        ProcurementApi.PurchaseReceiptView receipt = restTemplate.postForEntity(
                "/api/ivs/purchase-orders/" + order.id() + "/receive",
                Map.of("warehouseId", warehouse.id().toString(), "lines", receiveLines),
                ProcurementApi.PurchaseReceiptView.class).getBody();

        assertThat(receipt).isNotNull();
        assertThat(receipt.amount()).isEqualByComparingTo("840000.00");
        assertThat(receipt.lines().get(0).serialNos()).hasSize(2);

        ProcurementApi.PurchaseOrderView reloaded = restTemplate
                .getForEntity("/api/ivs/purchase-orders/" + order.id(), ProcurementApi.PurchaseOrderView.class).getBody();
        assertThat(reloaded).isNotNull();
        assertThat(reloaded.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(reloaded.lines().get(0).receivedQuantity()).isEqualByComparingTo("2.000");

        // 库存余额：在库 2 台，金额 = 2 × 420000
        InventoryApi.BalanceView[] balances = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + warehouse.id(), InventoryApi.BalanceView[].class).getBody();
        assertThat(balances).isNotNull().hasSize(1);
        assertThat(balances[0].onHandQty()).isEqualByComparingTo("2.000");
        assertThat(balances[0].amount()).isEqualByComparingTo("840000.00");
        assertThat(balances[0].productName()).isEqualTo("Q3 牵引车");

        InventoryApi.VehicleOrderStockView[] orderStock = restTemplate.getForEntity(
                "/api/ivs/inventory/vehicle-order-stock?warehouseId=" + warehouse.id(),
                InventoryApi.VehicleOrderStockView[].class).getBody();
        assertThat(orderStock).isNotNull().hasSize(1);
        assertThat(orderStock[0].purchaseOrderId()).isEqualTo(order.id());
        assertThat(orderStock[0].onHandQty()).isEqualByComparingTo("2.000");
        String expectedOrderNo = order.orderNo();

        // 台账：两条按序列号的入库记录
        InventoryApi.LedgerView[] ledger = restTemplate.getForEntity(
                "/api/ivs/inventory/ledger?productId=" + product.id(), InventoryApi.LedgerView[].class).getBody();
        assertThat(ledger).isNotNull().hasSize(2);
        assertThat(ledger).allSatisfy(item -> {
            assertThat(item.direction().name()).isEqualTo("IN");
            assertThat(item.moveType().name()).isEqualTo("PURCHASE_IN");
            assertThat(item.serialNo()).isNotBlank();
            assertThat(item.bizNo()).startsWith("RC-");
            assertThat(item.purchaseOrderNo()).isEqualTo(expectedOrderNo);
        });
        InventoryApi.LedgerView[] filteredLedger = restTemplate.getForEntity(
                "/api/ivs/inventory/ledger?warehouseId=" + warehouse.id() + "&moveType=PURCHASE_IN",
                InventoryApi.LedgerView[].class).getBody();
        assertThat(filteredLedger).isNotNull().hasSize(2);
        InventoryApi.LedgerView[] noSaleOut = restTemplate.getForEntity(
                "/api/ivs/inventory/ledger?warehouseId=" + warehouse.id() + "&moveType=SALE_OUT",
                InventoryApi.LedgerView[].class).getBody();
        assertThat(noSaleOut).isNotNull().isEmpty();

        // 序列号状态推进到在库，并回写入库时间
        for (UUID serialId : List.of(serial1.id(), serial2.id())) {
            MasterdataApi.SerialView serial = restTemplate.getForEntity("/api/ivs/product-serials/" + serialId,
                    MasterdataApi.SerialView.class).getBody();
            assertThat(serial).isNotNull();
            assertThat(serial.status()).isEqualTo(SerialStatus.IN_STOCK);
            assertThat(serial.inboundAt()).isNotNull();
            assertThat(serial.warehouseName()).isEqualTo("主仓");
        }
    }

    @Test
    void 数量商品收货按移动加权平均计算成本() {
        MasterdataApi.CategoryView part = createCategory("PART", "配件", TrackingMode.QUANTITY, "SIMPLE", "件");
        MasterdataApi.ProductView product = createProduct("机油滤芯", part.id(), "件", null, null, "FL-100");
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-02", "备件仓");
        MasterdataApi.SupplierView supplier = createSupplier("某配件商", SupplierType.OTHER);

        receiveQuantity(product, warehouse, supplier, "10", "100");
        receiveQuantity(product, warehouse, supplier, "10", "140");

        InventoryApi.BalanceView[] balances = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + warehouse.id(), InventoryApi.BalanceView[].class).getBody();
        assertThat(balances).isNotNull().hasSize(1);
        // 移动加权： (10×100 + 10×140) / 20 = 120
        assertThat(balances[0].onHandQty()).isEqualByComparingTo("20.000");
        assertThat(balances[0].unitCost()).isEqualByComparingTo("120.00");
        assertThat(balances[0].amount()).isEqualByComparingTo("2400.00");
    }

    @Test
    void 分批收货推进采购单状态() {
        MasterdataApi.CategoryView part = createCategory("PART", "配件", TrackingMode.QUANTITY, "SIMPLE", "件");
        MasterdataApi.ProductView product = createProduct("刹车片", part.id(), "件", null, null, "BP-1");
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-03", "备件仓");
        MasterdataApi.SupplierView supplier = createSupplier("某配件商", SupplierType.OTHER);

        ProcurementApi.PurchaseOrderView order = createOrder(supplier.id(), warehouse.id(),
                List.of(new ProcurementApi.SavePurchaseOrderLineCommand(product.id(), new BigDecimal("5"),
                        new BigDecimal("200"), null, null)));
        order = approve(order.id());

        order = receive(order, warehouse, order.lines().get(0).id(), "2", null);
        assertThat(order.status()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        assertThat(order.lines().get(0).remainingQuantity()).isEqualByComparingTo("3.000");

        order = receive(order, warehouse, order.lines().get(0).id(), "3", null);
        assertThat(order.status()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(order.lines().get(0).remainingQuantity()).isEqualByComparingTo("0.000");

        ResponseEntity<Map> repeated = receiveRaw(order.id(), warehouse.id(), List.of(
                Map.of("orderLineId", order.lines().get(0).id().toString(), "quantity", 1)));
        assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(repeated.getBody().get("code")).isEqualTo("PURCHASE_ALREADY_RECEIVED");
        assertThat(repeated.getBody().get("message")).isEqualTo("采购单已全部收货，无需重复收货");
    }

    @Test
    void 收货校验拦截非法请求() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE", "整车", TrackingMode.SERIAL, "VEHICLE", "台");
        MasterdataApi.ProductView car = createProduct("T31 自卸车", vehicle.id(), "台", "某厂", "T31", "自卸");
        MasterdataApi.CategoryView part = createCategory("PART", "配件", TrackingMode.QUANTITY, "SIMPLE", "件");
        MasterdataApi.ProductView bolt = createProduct("轮胎螺栓", part.id(), "件", null, null, "BOLT");
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-04", "主仓");
        MasterdataApi.SupplierView oem = createSupplier("某整车厂", SupplierType.OEM);

        ProcurementApi.PurchaseOrderView order = createOrder(oem.id(), warehouse.id(), List.of(
                new ProcurementApi.SavePurchaseOrderLineCommand(car.id(), new BigDecimal("2"), new BigDecimal("400000"), null, null),
                new ProcurementApi.SavePurchaseOrderLineCommand(bolt.id(), new BigDecimal("5"), new BigDecimal("10"), null, null)));

        // 未审核不允许收货
        ResponseEntity<Map> notApproved = receiveRaw(order.id(), warehouse.id(), List.of(
                Map.of("orderLineId", order.lines().get(0).id().toString(), "serialNos", List.of("LZZ1CLWB0NA200001"))));
        assertThat(notApproved.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(notApproved.getBody().get("code")).isEqualTo("PURCHASE_STATUS_INVALID");

        order = approve(order.id());

        // 序列号商品未提供 VIN
        ResponseEntity<Map> missingSerial = receiveRaw(order.id(), warehouse.id(), List.of(
                Map.of("orderLineId", order.lines().get(0).id().toString(), "quantity", 2)));
        assertThat(missingSerial.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(missingSerial.getBody().get("code")).isEqualTo("RECEIVE_SERIAL_REQUIRED");

        // 数量商品提供了序列号
        ResponseEntity<Map> serialNotAllowed = receiveRaw(order.id(), warehouse.id(), List.of(
                Map.of("orderLineId", order.lines().get(1).id().toString(), "serialNos", List.of("XXX-1"))));
        assertThat(serialNotAllowed.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(serialNotAllowed.getBody().get("code")).isEqualTo("RECEIVE_SERIAL_NOT_ALLOWED");

        // 同一次请求内 VIN 重复必须明确拒绝
        ResponseEntity<Map> duplicateSerial = receiveRaw(order.id(), warehouse.id(), List.of(
                Map.of("orderLineId", order.lines().get(0).id().toString(), "serialNos",
                        List.of("LZZ1CLWB0NA288888", "LZZ1CLWB0NA288888"))));
        assertThat(duplicateSerial.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicateSerial.getBody().get("code")).isEqualTo("RECEIVE_SERIAL_DUPLICATE");
        assertThat(duplicateSerial.getBody().get("message")).isEqualTo("有重复项，请核查后再提交");

        // 未建档的 VIN：采购收货时自动建立 VIN 档案
        ResponseEntity<Map> autoRegistered = receiveRaw(order.id(), warehouse.id(), List.of(
                Map.of("orderLineId", order.lines().get(0).id().toString(), "serialNos", List.of("LZZ1CLWB0NA299999"),
                        "unitPrice", 405000)));
        assertThat(autoRegistered.getStatusCode()).isEqualTo(HttpStatus.OK);
        MasterdataApi.SerialView[] autoSerials = restTemplate.getForEntity(
                "/api/ivs/product-serials?keyword=LZZ1CLWB0NA299999", MasterdataApi.SerialView[].class).getBody();
        assertThat(autoSerials).isNotNull().hasSize(1);
        assertThat(autoSerials[0].status().name()).isEqualTo("IN_STOCK");
        assertThat(autoSerials[0].purchasePrice()).isEqualByComparingTo("405000.00");

        // 超量收货
        ResponseEntity<Map> exceeded = receiveRaw(order.id(), warehouse.id(), List.of(
                Map.of("orderLineId", order.lines().get(1).id().toString(), "quantity", 6)));
        assertThat(exceeded.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exceeded.getBody().get("code")).isEqualTo("RECEIVE_QUANTITY_EXCEEDED");
    }

    @Test
    void 锁货占用可用库存并可释放与超时回收() {
        MasterdataApi.CategoryView part = createCategory("PART", "配件", TrackingMode.QUANTITY, "SIMPLE", "件");
        MasterdataApi.ProductView product = createProduct("车载充电机", part.id(), "件", null, null, "OBC-1");
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-05", "备件仓");
        MasterdataApi.SupplierView supplier = createSupplier("某配件商", SupplierType.OTHER);
        receiveQuantity(product, warehouse, supplier, "5", "3000");

        // 锁定 2 件：可用降为 3
        InventoryApi.LockView lock = restTemplate.postForEntity("/api/ivs/stocks/locks", Map.of(
                "productId", product.id().toString(), "warehouseId", warehouse.id().toString(),
                "quantity", 2, "bizType", "SALES_ORDER", "bizNo", "SO-TEST-1", "validDays", 7
        ), InventoryApi.LockView.class).getBody();
        assertThat(lock).isNotNull();
        assertThat(lock.status().name()).isEqualTo("ACTIVE");

        InventoryApi.BalanceView[] locked = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + warehouse.id(), InventoryApi.BalanceView[].class).getBody();
        assertThat(locked).isNotNull();
        assertThat(locked[0].lockedQty()).isEqualByComparingTo("2.000");
        assertThat(locked[0].availableQty()).isEqualByComparingTo("3.000");

        // 释放后可用恢复
        restTemplate.postForEntity("/api/ivs/stocks/locks/" + lock.id() + "/release", null, InventoryApi.LockView.class);
        InventoryApi.BalanceView[] released = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + warehouse.id(), InventoryApi.BalanceView[].class).getBody();
        assertThat(released).isNotNull();
        assertThat(released[0].availableQty()).isEqualByComparingTo("5.000");

        // 有效期为 0 天 → 立即过期，手工触发超时回收
        restTemplate.postForEntity("/api/ivs/stocks/locks", Map.of(
                "productId", product.id().toString(), "warehouseId", warehouse.id().toString(),
                "quantity", 4, "bizType", "SALES_ORDER", "bizNo", "SO-TEST-2", "validDays", 0
        ), InventoryApi.LockView.class);
        Integer released2 = restTemplate.postForEntity("/api/ivs/stocks/locks/release-expired", null, Integer.class).getBody();
        assertThat(released2).isEqualTo(1);
        InventoryApi.BalanceView[] afterExpiry = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + warehouse.id(), InventoryApi.BalanceView[].class).getBody();
        assertThat(afterExpiry).isNotNull();
        assertThat(afterExpiry[0].availableQty()).isEqualByComparingTo("5.000");
    }

    @Test
    void 台账与余额一致且看板可汇总() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE", "整车", TrackingMode.SERIAL, "VEHICLE", "台");
        MasterdataApi.ProductView product = createProduct("Q3 牵引车", vehicle.id(), "台", "某厂", "Q3", "T31");
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-06", "主仓");
        MasterdataApi.SupplierView oem = createSupplier("某整车厂", SupplierType.OEM);
        createSerial("LZZ1CLWB0NA300001", product.id());

        ProcurementApi.PurchaseOrderView order = createOrder(oem.id(), warehouse.id(),
                List.of(new ProcurementApi.SavePurchaseOrderLineCommand(product.id(), BigDecimal.ONE,
                        new BigDecimal("430000"), null, null)));
        order = approve(order.id());
        receive(order, warehouse, order.lines().get(0).id(), null, List.of("LZZ1CLWB0NA300001"));

        MasterdataApi.SerialView lockedVehicle = createSerial("LZZ1CLWB0NA300002", product.id());
        jdbcTemplate.update("update ivs.product_serial set status = 'LOCKED' where id = ?", lockedVehicle.id());
        MasterdataApi.CategoryView battery = createCategory("BATTERY", "动力电池", TrackingMode.SERIAL, "BATTERY", "块");
        MasterdataApi.ProductView batteryProduct = createProduct("重卡动力电池", battery.id(), "块", "某厂", "Q3电池", "BAT-1");
        MasterdataApi.SerialView batterySerial = createSerial("BAT300001", batteryProduct.id());
        jdbcTemplate.update("update ivs.product_serial set status = 'IN_STOCK' where id = ?", batterySerial.id());

        InventoryApi.ReconciliationView reconciliation = restTemplate.getForEntity(
                "/api/ivs/stocks/reconciliation", InventoryApi.ReconciliationView.class).getBody();
        assertThat(reconciliation).isNotNull();
        assertThat(reconciliation.consistent()).isTrue();
        assertThat(reconciliation.differences()).isEmpty();
        assertThat(reconciliation.ledgerCount()).isEqualTo(1);

        InventoryApi.SummaryView summary = restTemplate.getForEntity(
                "/api/ivs/inventory/summary", InventoryApi.SummaryView.class).getBody();
        assertThat(summary).isNotNull();
        assertThat(summary.serialInStock()).isEqualTo(2);
        assertThat(summary.vehicleInStockNotLocked()).isEqualTo(1);
        assertThat(summary.batteryInStock()).isEqualTo(1);
        assertThat(summary.vehicleSeriesInventory()).hasSize(1);
        assertThat(summary.vehicleSeriesInventory().get(0).series()).isEqualTo("Q3");
        assertThat(summary.vehicleSeriesInventory().get(0).unsoldVinCount()).isEqualTo(1);
        assertThat(summary.vehicleSeriesInventory().get(0).lockedVinCount()).isEqualTo(1);
        assertThat(summary.totalAmount()).isEqualByComparingTo("430000.00");
        assertThat(summary.ageBuckets()).hasSize(4);
        assertThat(summary.consistent()).isTrue();
    }

    /* ===================== 辅助方法 ===================== */

    private MasterdataApi.CategoryView createCategory(String code, String name, TrackingMode mode,
                                                      String template, String unit) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("trackingMode", mode.name());
        body.put("statusTemplate", template);
        body.put("unit", unit);
        body.put("sortOrder", 0);
        return restTemplate.postForEntity("/api/ivs/product-categories", body,
                MasterdataApi.CategoryView.class).getBody();
    }

    private MasterdataApi.ProductView createProduct(String name, UUID categoryId, String unit,
                                                    String brand, String series, String model) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryId", categoryId.toString());
        body.put("unit", unit);
        if (brand != null) body.put("brand", brand);
        if (series != null) body.put("series", series);
        if (model != null) body.put("model", model);
        return restTemplate.postForEntity("/api/ivs/products", body, MasterdataApi.ProductView.class).getBody();
    }

    private MasterdataApi.WarehouseView createWarehouse(String code, String name) {
        return restTemplate.postForEntity("/api/ivs/warehouses", Map.of(
                "code", code, "name", name, "type", WarehouseType.MAIN.name()
        ), MasterdataApi.WarehouseView.class).getBody();
    }

    private MasterdataApi.SupplierView createSupplier(String name, SupplierType type) {
        return restTemplate.postForEntity("/api/ivs/suppliers", Map.of(
                "name", name, "supplierType", type.name()
        ), MasterdataApi.SupplierView.class).getBody();
    }

    private MasterdataApi.SerialView createSerial(String serialNo, UUID productId) {
        return restTemplate.postForEntity("/api/ivs/product-serials", Map.of(
                "serialNo", serialNo, "productId", productId.toString()
        ), MasterdataApi.SerialView.class).getBody();
    }

    private ProcurementApi.PurchaseOrderView createOrder(UUID supplierId, UUID warehouseId,
                                                         List<ProcurementApi.SavePurchaseOrderLineCommand> lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("supplierId", supplierId.toString());
        body.put("warehouseId", warehouseId.toString());
        List<Map<String, Object>> linePayload = new ArrayList<>();
        for (ProcurementApi.SavePurchaseOrderLineCommand line : lines) {
            Map<String, Object> item = new HashMap<>();
            item.put("productId", line.productId().toString());
            item.put("quantity", line.quantity());
            item.put("unitPrice", line.unitPrice());
            if (line.taxRate() != null) item.put("taxRate", line.taxRate());
            linePayload.add(item);
        }
        body.put("lines", linePayload);
        return restTemplate.postForEntity("/api/ivs/purchase-orders", body,
                ProcurementApi.PurchaseOrderView.class).getBody();
    }

    private ProcurementApi.PurchaseOrderView approve(UUID orderId) {
        return restTemplate.postForEntity("/api/ivs/purchase-orders/" + orderId + "/approve", null,
                ProcurementApi.PurchaseOrderView.class).getBody();
    }

    private ResponseEntity<Map> receiveRaw(UUID orderId, UUID warehouseId, List<Map<String, Object>> lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("warehouseId", warehouseId.toString());
        body.put("lines", lines);
        return restTemplate.postForEntity("/api/ivs/purchase-orders/" + orderId + "/receive", body, Map.class);
    }

    private ProcurementApi.PurchaseOrderView receive(ProcurementApi.PurchaseOrderView order,
                                                     MasterdataApi.WarehouseView warehouse, UUID orderLineId,
                                                     String quantity, List<String> serialNos) {
        Map<String, Object> line = new HashMap<>();
        line.put("orderLineId", orderLineId.toString());
        if (quantity != null) line.put("quantity", new BigDecimal(quantity));
        if (serialNos != null) line.put("serialNos", serialNos);
        Map<String, Object> body = new HashMap<>();
        body.put("warehouseId", warehouse.id().toString());
        body.put("lines", List.of(line));
        ProcurementApi.PurchaseReceiptView receipt = restTemplate.postForEntity(
                "/api/ivs/purchase-orders/" + order.id() + "/receive", body,
                ProcurementApi.PurchaseReceiptView.class).getBody();
        assertThat(receipt).isNotNull();
        return restTemplate.getForEntity("/api/ivs/purchase-orders/" + order.id(),
                ProcurementApi.PurchaseOrderView.class).getBody();
    }

    private void receiveQuantity(MasterdataApi.ProductView product, MasterdataApi.WarehouseView warehouse,
                                 MasterdataApi.SupplierView supplier, String quantity, String unitPrice) {
        ProcurementApi.PurchaseOrderView order = createOrder(supplier.id(), warehouse.id(),
                List.of(new ProcurementApi.SavePurchaseOrderLineCommand(product.id(), new BigDecimal(quantity),
                        new BigDecimal(unitPrice), null, null)));
        order = approve(order.id());
        receive(order, warehouse, order.lines().get(0).id(), quantity, null);
    }
}
