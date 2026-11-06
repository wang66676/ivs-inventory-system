package com.ivs.app;

import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.OwnershipType;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.procurement.purchase.api.ProcurementApi;
import com.ivs.sales.order.SalesOrderStatus;
import com.ivs.sales.order.api.SalesApi;
import com.ivs.sales.settlement.PaymentMethod;
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

import static org.assertj.core.api.Assertions.assertThat;

/** 分期收款/以租代售：首付、期次、收款、逾期视图与所有权转移。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class InstallmentPlanIntegrationTest {

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
    void 分期收款生成首付与期次并在结清后转移所有权() {
        MasterdataApi.CategoryView vehicle = post("/api/ivs/product-categories", Map.of(
                "code", "VEHICLE-IP", "name", "整车", "trackingMode", TrackingMode.SERIAL.name(),
                "statusTemplate", "VEHICLE", "unit", "台", "sortOrder", 0), MasterdataApi.CategoryView.class);
        Map<String, Object> productBody = new HashMap<>();
        productBody.put("name", "Q3 牵引车");
        productBody.put("categoryId", vehicle.id().toString());
        productBody.put("unit", "台");
        productBody.put("series", "Q3");
        productBody.put("model", "T31");
        productBody.put("minPrice", 300000);
        productBody.put("salePriceGuide", 460000);
        MasterdataApi.ProductView product = post("/api/ivs/products", productBody, MasterdataApi.ProductView.class);
        MasterdataApi.WarehouseView warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "IP-WH", "name", "整车仓", "type", WarehouseType.MAIN.name()), MasterdataApi.WarehouseView.class);
        MasterdataApi.SupplierView supplier = post("/api/ivs/suppliers", Map.of(
                "name", "分期整车厂", "supplierType", SupplierType.OEM.name()), MasterdataApi.SupplierView.class);
        MasterdataApi.CustomerView customer = post("/api/ivs/customers", Map.of(
                "name", "分期客户", "customerType", CustomerType.COMPANY.name(), "creditLimit", 5000000),
                MasterdataApi.CustomerView.class);
        MasterdataApi.SerialView serial = post("/api/ivs/product-serials", Map.of(
                "serialNo", "IP-VIN-001", "productId", product.id().toString()), MasterdataApi.SerialView.class);

        ProcurementApi.PurchaseOrderView purchase = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.id().toString(), "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1,
                        "unitPrice", 420000))), ProcurementApi.PurchaseOrderView.class);
        purchase = post("/api/ivs/purchase-orders/" + purchase.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        post("/api/ivs/purchase-orders/" + purchase.id() + "/receive", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", purchase.lines().get(0).id().toString(),
                        "serialNos", List.of("IP-VIN-001")))), ProcurementApi.PurchaseReceiptView.class);

        LocalDate firstDue = LocalDate.now().plusMonths(1);
        SalesApi.SalesOrderView sales = post("/api/ivs/sales-orders", Map.of(
                "customerId", customer.id().toString(), "warehouseId", warehouse.id().toString(),
                "paymentMode", "INSTALLMENT", "downPayment", 100000, "installmentCount", 3,
                "firstDueDate", firstDue.toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1,
                        "unitPrice", 460000, "serialIds", List.of(serial.id().toString())))),
                SalesApi.SalesOrderView.class);
        assertThat(sales.paymentMode()).isEqualTo("INSTALLMENT");
        assertThat(sales.downPayment()).isEqualByComparingTo("100000.00");
        sales = post("/api/ivs/sales-orders/" + sales.id() + "/confirm", Map.of(), SalesApi.SalesOrderView.class);
        sales = post("/api/ivs/sales-orders/" + sales.id() + "/lock", Map.of(), SalesApi.SalesOrderView.class);
        post("/api/ivs/sales-orders/" + sales.id() + "/deliver", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", sales.lines().get(0).id().toString(),
                        "serialIds", List.of(serial.id().toString())))), SalesApi.SalesDeliveryView.class);

        SalesApi.InstallmentPlanView[] plans = restTemplate.getForEntity(
                "/api/ivs/installment-plans", SalesApi.InstallmentPlanView[].class).getBody();
        assertThat(plans).isNotNull().hasSize(1);
        SalesApi.InstallmentPlanView plan = plans[0];
        assertThat(plan.financedAmount()).isEqualByComparingTo("360000.00");
        assertThat(plan.schedules()).hasSize(4).allMatch(item -> item.outstanding().signum() > 0);
        assertThat(serialById(serial.id()).ownershipType()).isEqualTo(OwnershipType.LEASE_TO_OWN);

        SalesApi.InstallmentScheduleView down = plan.schedules().stream()
                .filter(item -> item.periodNo() == 0).findFirst().orElseThrow();
        post("/api/ivs/receipts", Map.of("customerId", customer.id().toString(), "amount", 100000,
                "method", PaymentMethod.BANK_TRANSFER.name(), "receivableIds", List.of(down.receivableId().toString())),
                SalesApi.ReceiptView.class);
        assertThat(getPlan(plan.id()).status()).isEqualTo("ACTIVE");

        List<String> remainingIds = getPlan(plan.id()).schedules().stream()
                .filter(item -> item.outstanding().signum() > 0).map(item -> item.receivableId().toString()).toList();
        post("/api/ivs/receipts", Map.of("customerId", customer.id().toString(), "amount", 360000,
                "method", PaymentMethod.BANK_TRANSFER.name(), "receivableIds", remainingIds),
                SalesApi.ReceiptView.class);

        SalesApi.InstallmentPlanView settled = getPlan(plan.id());
        assertThat(settled.status()).isEqualTo("SETTLED");
        assertThat(settled.outstanding()).isEqualByComparingTo("0.00");
        assertThat(settled.overdueAmount()).isEqualByComparingTo("0.00");
        assertThat(serialById(serial.id()).ownershipType()).isEqualTo(OwnershipType.OWNED);
        SalesApi.SalesOrderView closed = restTemplate.getForEntity(
                "/api/ivs/sales-orders/" + sales.id(), SalesApi.SalesOrderView.class).getBody();
        assertThat(closed).isNotNull();
        assertThat(closed.status()).isEqualTo(SalesOrderStatus.CLOSED);
    }

    private SalesApi.InstallmentPlanView getPlan(java.util.UUID id) {
        return restTemplate.getForEntity("/api/ivs/installment-plans/" + id, SalesApi.InstallmentPlanView.class).getBody();
    }

    private MasterdataApi.SerialView serialById(java.util.UUID id) {
        return restTemplate.getForEntity("/api/ivs/product-serials/" + id, MasterdataApi.SerialView.class).getBody();
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}
