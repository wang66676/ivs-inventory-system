package com.ivs.app;

import com.ivs.billing.invoice.InvoiceStatus;
import com.ivs.billing.invoice.InvoiceType;
import com.ivs.billing.invoice.api.BillingApi;
import com.ivs.inventory.stock.api.InventoryApi;
import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.procurement.purchase.api.ProcurementApi;
import com.ivs.sales.order.SalesOrderStatus;
import com.ivs.sales.order.api.SalesApi;
import com.ivs.sales.settlement.ReceivableStatus;
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
 * M3 销售与结算验收：整车销售全链路（取价 → 低价审批 → 确认 → 锁货 → 出库 → 应收 → 收款 → 开票 → 红冲）、
 * 信用额度拦截、库存不足拦截、部分出库、收款超额拦截。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SalesSettlementIntegrationTest {

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
    void 整车销售全链路从下单到收款开票红冲() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE", "整车", TrackingMode.SERIAL, "VEHICLE", "台");
        MasterdataApi.ProductView product = createProduct("Q3 牵引车", vehicle.id(), "台", "某厂", "Q3", "T31",
                new BigDecimal("430000"), new BigDecimal("460000"));
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-S1", "整车仓");
        MasterdataApi.SupplierView oem = createSupplier("某整车厂", SupplierType.OEM);
        MasterdataApi.CustomerView customer = createCustomer("上海某物流有限公司", CustomerType.COMPANY,
                new BigDecimal("5000000"), 30);

        // 先进货 1 台（采购价 420000），形成库存
        MasterdataApi.SerialView serial = createSerial("LZZ1CLWB0NA900001", product.id());
        purchaseAndReceive(oem, warehouse, product, serial);

        // 建单：不传单价 → 自动取价（命中价目表或回落指导价 460000）
        SalesApi.SalesOrderView order = createOrder(customer, warehouse,
                List.of(new SalesApi.SaveSalesOrderLineCommand(product.id(), BigDecimal.ONE, null,
                        List.of(serial.id()), null)));
        assertThat(order.status()).isEqualTo(SalesOrderStatus.DRAFT);
        assertThat(order.lines().get(0).unitPrice()).isEqualByComparingTo("460000.00");
        assertThat(order.lines().get(0).remainingSerialIds()).containsExactly(serial.id());
        assertThat(order.priceApprovalRequired()).isFalse();

        order = post("/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(), SalesApi.SalesOrderView.class);
        assertThat(order.status()).isEqualTo(SalesOrderStatus.CONFIRMED);

        // 锁货：序列号进入锁定状态
        order = post("/api/ivs/sales-orders/" + order.id() + "/lock", Map.of(), SalesApi.SalesOrderView.class);
        MasterdataApi.SerialView locked = getSerial(serial.id());
        assertThat(locked.status()).isEqualTo(SerialStatus.LOCKED);
        InventoryApi.BalanceView balance = balanceOf(warehouse, product);
        assertThat(balance.lockedQty()).isEqualByComparingTo("1.000");
        assertThat(balance.availableQty()).isEqualByComparingTo("0.000");

        // 出库：写台账、结转成本、生成应收
        SalesApi.SalesDeliveryView delivery = post("/api/ivs/sales-orders/" + order.id() + "/deliver",
                Map.of("warehouseId", warehouse.id().toString(), "lines", List.of(
                        Map.of("orderLineId", order.lines().get(0).id().toString(),
                                "serialIds", List.of(serial.id().toString())))),
                SalesApi.SalesDeliveryView.class);
        assertThat(delivery.amount()).isEqualByComparingTo("460000.00");
        assertThat(delivery.costAmount()).isEqualByComparingTo("420000.00");
        assertThat(delivery.grossProfit()).isEqualByComparingTo("40000.00");

        order = get("/api/ivs/sales-orders/" + order.id(), SalesApi.SalesOrderView.class);
        assertThat(order.status()).isEqualTo(SalesOrderStatus.DELIVERED);
        assertThat(order.lines().get(0).remainingSerialIds()).isEmpty();

        assertThat(getSerial(serial.id()).status()).isEqualTo(SerialStatus.DELIVERED);
        InventoryApi.BalanceView afterDelivery = balanceOf(warehouse, product);
        assertThat(afterDelivery.onHandQty()).isEqualByComparingTo("0.000");
        assertThat(afterDelivery.lockedQty()).isEqualByComparingTo("0.000");

        InventoryApi.LedgerView[] ledger = restTemplate.getForEntity(
                "/api/ivs/inventory/ledger?productId=" + product.id(), InventoryApi.LedgerView[].class).getBody();
        assertThat(ledger).isNotNull();
        assertThat(ledger).anySatisfy(item -> {
            assertThat(item.moveType().name()).isEqualTo("SALE_OUT");
            assertThat(item.amount()).isEqualByComparingTo("420000.00");
        });

        SalesApi.ReceivableView[] receivables = restTemplate.getForEntity(
                "/api/ivs/receivables?customerId=" + customer.id(), SalesApi.ReceivableView[].class).getBody();
        assertThat(receivables).isNotNull().hasSize(1);
        assertThat(receivables[0].amount()).isEqualByComparingTo("460000.00");
        assertThat(receivables[0].status()).isEqualTo(ReceivableStatus.OPEN);

        // 收款核销
        SalesApi.ReceiptView receipt = post("/api/ivs/receipts", Map.of(
                "customerId", customer.id().toString(), "amount", 460000, "method", "BANK_TRANSFER",
                "remark", "全款到账"), SalesApi.ReceiptView.class);
        assertThat(receipt.allocations()).hasSize(1);
        assertThat(receipt.allocations().get(0).amount()).isEqualByComparingTo("460000.00");
        SalesApi.ReceivableView[] settled = restTemplate.getForEntity(
                "/api/ivs/receivables?customerId=" + customer.id(), SalesApi.ReceivableView[].class).getBody();
        assertThat(settled).isNotNull();
        assertThat(settled[0].status()).isEqualTo(ReceivableStatus.SETTLED);
        // 款货两清 → 订单关闭
        assertThat(get("/api/ivs/sales-orders/" + order.id(), SalesApi.SalesOrderView.class).status())
                .isEqualTo(SalesOrderStatus.CLOSED);

        // 开票（含税 460000，税率 13%）
        BillingApi.InvoiceView invoice = post("/api/ivs/sales-invoices", Map.of(
                "orderId", order.id().toString(), "taxRate", "0.13"), BillingApi.InvoiceView.class);
        assertThat(invoice.amount()).isEqualByComparingTo("460000.00");
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.DRAFT);
        invoice = post("/api/ivs/sales-invoices/" + invoice.id() + "/issue", Map.of(
                "externalInvoiceNo", "INV-EXT-0001", "taxRate", "0.13"), BillingApi.InvoiceView.class);
        assertThat(invoice.status()).isEqualTo(InvoiceStatus.ISSUED);
        assertThat(invoice.taxAmount()).isEqualByComparingTo("52920.35");

        // 红冲：生成关联原票的红字发票
        BillingApi.InvoiceView red = post("/api/ivs/sales-invoices/" + invoice.id() + "/red-flush", Map.of(),
                BillingApi.InvoiceView.class);
        assertThat(red.type()).isEqualTo(InvoiceType.RED);
        assertThat(red.originalInvoiceId()).isEqualTo(invoice.id());
        BillingApi.InvoiceView original = get("/api/ivs/sales-invoices/" + invoice.id(), BillingApi.InvoiceView.class);
        assertThat(original.status()).isEqualTo(InvoiceStatus.RED_FLUSHED);
    }

    @Test
    void 草稿订单可后补VIN且未锁货不能出库() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE-EDIT", "整车", TrackingMode.SERIAL,
                "VEHICLE", "台");
        MasterdataApi.ProductView product = createProduct("Q3 后补VIN", vehicle.id(), "台", "某厂", "Q3", "T31",
                new BigDecimal("430000"), new BigDecimal("460000"));
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-EDIT", "整车仓");
        MasterdataApi.SupplierView oem = createSupplier("后补VIN整车厂", SupplierType.OEM);
        MasterdataApi.CustomerView customer = createCustomer("后补VIN客户", CustomerType.COMPANY,
                new BigDecimal("5000000"), 30);
        MasterdataApi.SerialView serial = createSerial("LZZ1CLWB0NA900099", product.id());
        purchaseAndReceive(oem, warehouse, product, serial);

        SalesApi.SalesOrderView order = createOrder(customer, warehouse,
                List.of(new SalesApi.SaveSalesOrderLineCommand(product.id(), BigDecimal.ONE,
                        new BigDecimal("460000"), null, null)));
        assertThat(order.lines().get(0).serialIds()).isEmpty();

        Map<String, Object> update = new HashMap<>();
        update.put("customerId", customer.id().toString());
        update.put("warehouseId", warehouse.id().toString());
        update.put("lines", List.of(Map.of(
                "productId", product.id().toString(),
                "quantity", 1,
                "unitPrice", 460000,
                "serialIds", List.of(serial.id().toString())
        )));
        SalesApi.SalesOrderView updated = restTemplate.exchange(
                "/api/ivs/sales-orders/" + order.id(), org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(update), SalesApi.SalesOrderView.class).getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.lines().get(0).serialIds()).containsExactly(serial.id());

        order = post("/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(), SalesApi.SalesOrderView.class);
        ResponseEntity<Map> deliverBeforeLock = restTemplate.postForEntity(
                "/api/ivs/sales-orders/" + order.id() + "/deliver",
                Map.of("warehouseId", warehouse.id().toString(), "lines", List.of(
                        Map.of("orderLineId", order.lines().get(0).id().toString(),
                                "serialIds", List.of(serial.id().toString())))), Map.class);
        assertThat(deliverBeforeLock.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(deliverBeforeLock.getBody().get("code")).isEqualTo("SALES_NOT_LOCKED");

        order = post("/api/ivs/sales-orders/" + order.id() + "/lock", Map.of(), SalesApi.SalesOrderView.class);
        assertThat(order.lockedAt()).isNotNull();
        assertThat(getSerial(serial.id()).status()).isEqualTo(SerialStatus.LOCKED);

        MasterdataApi.SerialView[] inStock = restTemplate.getForEntity(
                "/api/ivs/product-serials?status=IN_STOCK", MasterdataApi.SerialView[].class).getBody();
        assertThat(inStock).isNotNull();
        assertThat(inStock).noneMatch(item -> item.id().equals(serial.id()));
    }

    @Test
    void 低于最低限价需先价格审批才能确认() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE", "整车", TrackingMode.SERIAL, "VEHICLE", "台");
        MasterdataApi.ProductView product = createProduct("T31 自卸车", vehicle.id(), "台", "某厂", "T31", "自卸",
                new BigDecimal("430000"), new BigDecimal("460000"));
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-S2", "整车仓");
        MasterdataApi.CustomerView customer = createCustomer("某个人客户", CustomerType.INDIVIDUAL, null, 30);

        SalesApi.SalesOrderView order = createOrder(customer, warehouse,
                List.of(new SalesApi.SaveSalesOrderLineCommand(product.id(), BigDecimal.ONE,
                        new BigDecimal("400000"), null, null)));
        assertThat(order.priceApprovalRequired()).isTrue();

        ResponseEntity<Map> confirmBeforeApproval = restTemplate.postForEntity(
                "/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(), Map.class);
        assertThat(confirmBeforeApproval.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(confirmBeforeApproval.getBody().get("code")).isEqualTo("SALES_STATUS_INVALID");

        order = post("/api/ivs/sales-orders/" + order.id() + "/approve-price", Map.of(),
                SalesApi.SalesOrderView.class);
        assertThat(order.priceApproved()).isTrue();
        order = post("/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(), SalesApi.SalesOrderView.class);
        assertThat(order.status()).isEqualTo(SalesOrderStatus.CONFIRMED);
    }

    @Test
    void 超过信用额度无法确认订单() {
        MasterdataApi.CategoryView part = createCategory("PART", "配件", TrackingMode.QUANTITY, "SIMPLE", "件");
        MasterdataApi.ProductView product = createProduct("机油滤芯", part.id(), "件", null, null, "FL-100",
                BigDecimal.ZERO, new BigDecimal("100"));
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-S3", "备件仓");
        MasterdataApi.CustomerView customer = createCustomer("小额客户", CustomerType.COMPANY,
                new BigDecimal("1000"), 30);

        SalesApi.SalesOrderView order = createOrder(customer, warehouse,
                List.of(new SalesApi.SaveSalesOrderLineCommand(product.id(), new BigDecimal("100"), null, null, null)));
        ResponseEntity<Map> response = restTemplate.postForEntity(
                "/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("CREDIT_LIMIT_EXCEEDED");
    }

    @Test
    void 已取消销售订单可以删除() {
        MasterdataApi.CategoryView part = createCategory("PART-DEL", "配件", TrackingMode.QUANTITY, "SIMPLE", "件");
        MasterdataApi.ProductView product = createProduct("可删除订单商品", part.id(), "件", null, null, "DEL-1",
                BigDecimal.ZERO, new BigDecimal("100"));
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-DEL", "删除测试仓");
        MasterdataApi.CustomerView customer = createCustomer("删除测试客户", CustomerType.INDIVIDUAL, null, 30);
        SalesApi.SalesOrderView order = createOrder(customer, warehouse,
                List.of(new SalesApi.SaveSalesOrderLineCommand(product.id(), BigDecimal.ONE,
                        new BigDecimal("100"), null, null)));
        post("/api/ivs/sales-orders/" + order.id() + "/cancel", Map.of(), SalesApi.SalesOrderView.class);
        var deleted = restTemplate.exchange("/api/ivs/sales-orders/" + order.id(), org.springframework.http.HttpMethod.DELETE,
                null, Void.class);
        assertThat(deleted.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        var missing = restTemplate.getForEntity("/api/ivs/sales-orders/" + order.id(), Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 零价订单出库前返回业务提示并可补录成交价后出库() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE-ZERO-PRICE", "整车", TrackingMode.SERIAL,
                "VEHICLE", "台");
        MasterdataApi.ProductView product = createProduct("Q3 零价修复测试车", vehicle.id(), "台", "某厂", "Q3", "T31",
                BigDecimal.ZERO, BigDecimal.ZERO);
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-ZERO", "零价修复仓");
        MasterdataApi.SupplierView oem = createSupplier("零价修复整车厂", SupplierType.OEM);
        MasterdataApi.CustomerView customer = createCustomer("零价修复客户", CustomerType.COMPANY,
                new BigDecimal("5000000"), 30);
        MasterdataApi.SerialView serial = createSerial("LZZZEROPRICE000001", product.id());
        purchaseAndReceive(oem, warehouse, product, serial);

        ResponseEntity<Map> zeroCreate = restTemplate.postForEntity("/api/ivs/sales-orders", Map.of(
                "customerId", customer.id().toString(), "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1,
                        "unitPrice", 0, "serialIds", List.of(serial.id().toString())))), Map.class);
        assertThat(zeroCreate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(zeroCreate.getBody().get("code")).isEqualTo("SALES_PRICE_REQUIRED");

        SalesApi.SalesOrderView order = createOrder(customer, warehouse,
                List.of(new SalesApi.SaveSalesOrderLineCommand(product.id(), BigDecimal.ONE,
                        BigDecimal.ONE, List.of(serial.id()), null)));
        order = post("/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(), SalesApi.SalesOrderView.class);
        order = post("/api/ivs/sales-orders/" + order.id() + "/lock", Map.of(), SalesApi.SalesOrderView.class);
        UUID lineId = order.lines().get(0).id();

        // 模拟旧版本已经生成的 0 元订单，验证不会再抛 HTTP 500。
        jdbcTemplate.update("update ivs.sales_order_line set unit_price = 0, amount = 0 where id = ?", lineId);
        jdbcTemplate.update("update ivs.sales_order set total_amount = 0 where id = ?", order.id());
        ResponseEntity<Map> zeroDelivery = restTemplate.postForEntity(
                "/api/ivs/sales-orders/" + order.id() + "/deliver",
                Map.of("warehouseId", warehouse.id().toString(), "lines", List.of(
                        Map.of("orderLineId", lineId.toString(), "serialIds", List.of(serial.id().toString())))),
                Map.class);
        assertThat(zeroDelivery.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(zeroDelivery.getBody().get("code")).isEqualTo("SALES_PRICE_REQUIRED");

        SalesApi.SalesOrderView priced = restTemplate.exchange(
                "/api/ivs/sales-orders/" + order.id() + "/prices", org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of("lines", List.of(Map.of(
                        "orderLineId", lineId.toString(), "unitPrice", 450000)))),
                SalesApi.SalesOrderView.class).getBody();
        assertThat(priced).isNotNull();
        assertThat(priced.totalAmount()).isEqualByComparingTo("450000.00");
        assertThat(priced.lines().get(0).unitPrice()).isEqualByComparingTo("450000.00");

        SalesApi.SalesDeliveryView delivery = post("/api/ivs/sales-orders/" + order.id() + "/deliver",
                Map.of("warehouseId", warehouse.id().toString(), "lines", List.of(
                        Map.of("orderLineId", lineId.toString(), "serialIds", List.of(serial.id().toString())))),
                SalesApi.SalesDeliveryView.class);
        assertThat(delivery.amount()).isEqualByComparingTo("450000.00");
    }

    @Test
    void 库存不足无法出库且部分出库按剩余重新锁定() {
        MasterdataApi.CategoryView part = createCategory("PART", "配件", TrackingMode.QUANTITY, "SIMPLE", "件");
        MasterdataApi.ProductView product = createProduct("刹车片", part.id(), "件", null, null, "BP-1",
                BigDecimal.ZERO, new BigDecimal("300"));
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-S4", "备件仓");
        MasterdataApi.SupplierView supplier = createSupplier("某配件商", SupplierType.OTHER);
        MasterdataApi.CustomerView customer = createCustomer("某物流车队", CustomerType.FLEET, null, 30);

        // 进货 10 件
        purchaseQuantity(supplier, warehouse, product, "10", "200");

        // 库存不足：出库 20 件被拦截
        SalesApi.SalesOrderView shortageOrder = createOrder(customer, warehouse,
                List.of(new SalesApi.SaveSalesOrderLineCommand(product.id(), new BigDecimal("20"), null, null, null)));
        shortageOrder = post("/api/ivs/sales-orders/" + shortageOrder.id() + "/confirm", Map.of(), SalesApi.SalesOrderView.class);
        ResponseEntity<Map> shortage = restTemplate.postForEntity(
                "/api/ivs/sales-orders/" + shortageOrder.id() + "/lock", Map.of(), Map.class);
        assertThat(shortage.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(shortage.getBody().get("code")).isEqualTo("STOCK_NOT_AVAILABLE");

        // 部分出库：下单 6 件，先出 4 件
        SalesApi.SalesOrderView order = createOrder(customer, warehouse,
                List.of(new SalesApi.SaveSalesOrderLineCommand(product.id(), new BigDecimal("6"), null, null, null)));
        order = post("/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(), SalesApi.SalesOrderView.class);
        order = post("/api/ivs/sales-orders/" + order.id() + "/lock", Map.of(), SalesApi.SalesOrderView.class);
        UUID lineId = order.lines().get(0).id();

        SalesApi.SalesDeliveryView first = post("/api/ivs/sales-orders/" + order.id() + "/deliver",
                Map.of("warehouseId", warehouse.id().toString(),
                        "lines", List.of(Map.of("orderLineId", lineId.toString(), "quantity", 4))),
                SalesApi.SalesDeliveryView.class);
        assertThat(first.amount()).isEqualByComparingTo("1200.00");

        order = get("/api/ivs/sales-orders/" + order.id(), SalesApi.SalesOrderView.class);
        assertThat(order.status()).isEqualTo(SalesOrderStatus.PARTIALLY_DELIVERED);
        assertThat(order.lines().get(0).remainingQuantity()).isEqualByComparingTo("2.000");

        // 剩余 2 件应仍处于锁定状态（可用 = 在库 6 - 锁定 2 = 4）
        InventoryApi.BalanceView balance = balanceOf(warehouse, product);
        assertThat(balance.onHandQty()).isEqualByComparingTo("6.000");
        assertThat(balance.lockedQty()).isEqualByComparingTo("2.000");
        assertThat(balance.availableQty()).isEqualByComparingTo("4.000");

        // 出完剩余 2 件 → 订单完成
        post("/api/ivs/sales-orders/" + order.id() + "/deliver",
                Map.of("warehouseId", warehouse.id().toString(),
                        "lines", List.of(Map.of("orderLineId", lineId.toString(), "quantity", 2))),
                SalesApi.SalesDeliveryView.class);
        order = get("/api/ivs/sales-orders/" + order.id(), SalesApi.SalesOrderView.class);
        assertThat(order.status()).isEqualTo(SalesOrderStatus.DELIVERED);
        InventoryApi.BalanceView finished = balanceOf(warehouse, product);
        assertThat(finished.lockedQty()).isEqualByComparingTo("0.000");
        assertThat(finished.availableQty()).isEqualByComparingTo("4.000");
    }

    @Test
    void 收款金额超过未收总额被拒绝() {
        MasterdataApi.CategoryView part = createCategory("PART", "配件", TrackingMode.QUANTITY, "SIMPLE", "件");
        MasterdataApi.ProductView product = createProduct("车载充电机", part.id(), "件", null, null, "OBC-1",
                BigDecimal.ZERO, new BigDecimal("3000"));
        MasterdataApi.WarehouseView warehouse = createWarehouse("WH-S5", "备件仓");
        MasterdataApi.SupplierView supplier = createSupplier("某配件商", SupplierType.OTHER);
        MasterdataApi.CustomerView customer = createCustomer("某物流车队", CustomerType.FLEET, null, 30);
        purchaseQuantity(supplier, warehouse, product, "5", "2500");

        SalesApi.SalesOrderView order = createOrder(customer, warehouse,
                List.of(new SalesApi.SaveSalesOrderLineCommand(product.id(), BigDecimal.ONE, null, null, null)));
        order = post("/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(), SalesApi.SalesOrderView.class);
        order = post("/api/ivs/sales-orders/" + order.id() + "/lock", Map.of(), SalesApi.SalesOrderView.class);
        post("/api/ivs/sales-orders/" + order.id() + "/deliver",
                Map.of("warehouseId", warehouse.id().toString(),
                        "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(), "quantity", 1))),
                SalesApi.SalesDeliveryView.class);

        ResponseEntity<Map> tooMuch = restTemplate.postForEntity("/api/ivs/receipts", Map.of(
                "customerId", customer.id().toString(), "amount", 999999, "method", "CASH"), Map.class);
        assertThat(tooMuch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(tooMuch.getBody().get("code")).isEqualTo("RECEIPT_EXCEEDS_OUTSTANDING");

        // 部分收款 → 应收为部分核销
        SalesApi.ReceiptView partial = post("/api/ivs/receipts", Map.of(
                "customerId", customer.id().toString(), "amount", 1000, "method", "CASH"),
                SalesApi.ReceiptView.class);
        assertThat(partial.amount()).isEqualByComparingTo("1000.00");
        SalesApi.ReceivableView[] receivables = restTemplate.getForEntity(
                "/api/ivs/receivables?customerId=" + customer.id(), SalesApi.ReceivableView[].class).getBody();
        assertThat(receivables).isNotNull();
        assertThat(receivables[0].status()).isEqualTo(ReceivableStatus.PARTIALLY_SETTLED);
        assertThat(receivables[0].outstanding()).isEqualByComparingTo("2000.00");
    }

    /* ===================== 辅助方法 ===================== */

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }

    private <T> T get(String url, Class<T> type) {
        return restTemplate.getForEntity(url, type).getBody();
    }

    private MasterdataApi.CategoryView createCategory(String code, String name, TrackingMode mode,
                                                      String template, String unit) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("trackingMode", mode.name());
        body.put("statusTemplate", template);
        body.put("unit", unit);
        body.put("sortOrder", 0);
        return post("/api/ivs/product-categories", body, MasterdataApi.CategoryView.class);
    }

    private MasterdataApi.ProductView createProduct(String name, UUID categoryId, String unit, String brand,
                                                    String series, String model, BigDecimal minPrice,
                                                    BigDecimal guidePrice) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryId", categoryId.toString());
        body.put("unit", unit);
        if (brand != null) body.put("brand", brand);
        if (series != null) body.put("series", series);
        if (model != null) body.put("model", model);
        body.put("minPrice", minPrice);
        body.put("salePriceGuide", guidePrice);
        return post("/api/ivs/products", body, MasterdataApi.ProductView.class);
    }

    private MasterdataApi.WarehouseView createWarehouse(String code, String name) {
        return post("/api/ivs/warehouses", Map.of("code", code, "name", name,
                "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
    }

    private MasterdataApi.SupplierView createSupplier(String name, SupplierType type) {
        return post("/api/ivs/suppliers", Map.of("name", name, "supplierType", type.name()),
                MasterdataApi.SupplierView.class);
    }

    private MasterdataApi.CustomerView createCustomer(String name, CustomerType type, BigDecimal creditLimit,
                                                      int termDays) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("customerType", type.name());
        if (creditLimit != null) body.put("creditLimit", creditLimit);
        return post("/api/ivs/customers", body, MasterdataApi.CustomerView.class);
    }

    private MasterdataApi.SerialView createSerial(String serialNo, UUID productId) {
        return post("/api/ivs/product-serials", Map.of("serialNo", serialNo, "productId", productId.toString()),
                MasterdataApi.SerialView.class);
    }

    private MasterdataApi.SerialView getSerial(UUID serialId) {
        return get("/api/ivs/product-serials/" + serialId, MasterdataApi.SerialView.class);
    }

    private InventoryApi.BalanceView balanceOf(MasterdataApi.WarehouseView warehouse, MasterdataApi.ProductView product) {
        InventoryApi.BalanceView[] balances = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + warehouse.id(), InventoryApi.BalanceView[].class).getBody();
        assertThat(balances).isNotNull();
        return java.util.Arrays.stream(balances)
                .filter(item -> item.productId().equals(product.id()))
                .findFirst()
                .orElseThrow();
    }

    private SalesApi.SalesOrderView createOrder(MasterdataApi.CustomerView customer,
                                                MasterdataApi.WarehouseView warehouse,
                                                List<SalesApi.SaveSalesOrderLineCommand> lines) {
        Map<String, Object> body = new HashMap<>();
        body.put("customerId", customer.id().toString());
        body.put("warehouseId", warehouse.id().toString());
        List<Map<String, Object>> payload = new ArrayList<>();
        for (SalesApi.SaveSalesOrderLineCommand line : lines) {
            Map<String, Object> item = new HashMap<>();
            item.put("productId", line.productId().toString());
            item.put("quantity", line.quantity());
            if (line.unitPrice() != null) item.put("unitPrice", line.unitPrice());
            if (line.serialIds() != null) {
                item.put("serialIds", line.serialIds().stream().map(UUID::toString).toList());
            }
            payload.add(item);
        }
        body.put("lines", payload);
        return post("/api/ivs/sales-orders", body, SalesApi.SalesOrderView.class);
    }

    private void purchaseAndReceive(MasterdataApi.SupplierView supplier, MasterdataApi.WarehouseView warehouse,
                                    MasterdataApi.ProductView product, MasterdataApi.SerialView serial) {
        ProcurementApi.PurchaseOrderView order = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.id().toString(),
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1,
                        "unitPrice", 420000))), ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        restTemplate.postForEntity("/api/ivs/purchase-orders/" + order.id() + "/receive", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(),
                        "serialNos", List.of(serial.serialNo())))), ProcurementApi.PurchaseReceiptView.class);
    }

    private void purchaseQuantity(MasterdataApi.SupplierView supplier, MasterdataApi.WarehouseView warehouse,
                                  MasterdataApi.ProductView product, String quantity, String unitPrice) {
        ProcurementApi.PurchaseOrderView order = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.id().toString(),
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(),
                        "quantity", new BigDecimal(quantity), "unitPrice", new BigDecimal(unitPrice)))),
                ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        restTemplate.postForEntity("/api/ivs/purchase-orders/" + order.id() + "/receive", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(),
                        "quantity", new BigDecimal(quantity)))), ProcurementApi.PurchaseReceiptView.class);
    }
}
