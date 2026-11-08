package com.ivs.app;

import com.ivs.inventory.stock.api.InventoryApi;
import com.ivs.lease.LeaseOwnershipAtEnd;
import com.ivs.lease.LeaseStatus;
import com.ivs.lease.api.LeaseApi;
import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.OwnershipType;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.procurement.purchase.api.ProcurementApi;
import com.ivs.sales.settlement.PaymentMethod;
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
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 二期电池租赁：租赁资产池、合同、月租计划、应收与到期归还。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LeaseIntegrationTest {

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
    void 电池起租生成月租应收并在归还后恢复可售库存() {
        AssetFixture fixture = inStockAsset("LEASE", false);
        LocalDate start = LocalDate.of(2026, 12, 1);
        LeaseApi.LeaseContractView contract = createContract(fixture, start, start.plusMonths(3),
                new BigDecimal("10000.00"), LeaseOwnershipAtEnd.RETURN);

        assertThat(contract.status()).isEqualTo(LeaseStatus.ACTIVE);
        assertThat(contract.contractNo()).startsWith("LEASE-");
        assertThat(contract.schedules()).hasSize(3)
                .allMatch(item -> item.amount().compareTo(new BigDecimal("10000.00")) == 0)
                .allMatch(item -> item.receivableId() == null);
        assertThat(serialById(fixture.serial().id()).status()).isEqualTo(SerialStatus.IN_LEASE);
        assertThat(serialById(fixture.serial().id()).ownershipType()).isEqualTo(OwnershipType.LEASED);
        assertThat(leaseContractId(fixture.serial().id())).isEqualTo(contract.id());
        assertThat(balanceOf(fixture.serial()).onHandQty()).isEqualByComparingTo("0.000");
        assertThat(balanceOf(fixture.serial()).availableQty()).isEqualByComparingTo("0.000");

        ResponseEntity<Map> duplicate = restTemplate.postForEntity("/api/ivs/lease-contracts",
                contractBody(fixture, start.plusMonths(1), start.plusMonths(4), new BigDecimal("9000.00"),
                        LeaseOwnershipAtEnd.RETURN), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).containsEntry("code", "LEASE_SERIAL_NOT_AVAILABLE");

        LeaseApi.LeaseContractView withReceivables = post(
                "/api/ivs/lease-contracts/" + contract.id() + "/generate-due?throughDate=2027-02-15",
                Map.of(), LeaseApi.LeaseContractView.class);
        List<LeaseApi.LeaseScheduleView> generated = withReceivables.schedules().stream()
                .filter(item -> item.receivableId() != null).toList();
        assertThat(generated).hasSize(2)
                .allMatch(item -> item.receivableNo().startsWith("LR-"))
                .allMatch(item -> "OPEN".equals(item.receivableStatus()));

        ResponseEntity<Map> receipt = restTemplate.postForEntity("/api/ivs/receipts", Map.of(
                "customerId", fixture.customer().id().toString(),
                "amount", 20000,
                "method", PaymentMethod.BANK_TRANSFER.name(),
                "receivableIds", generated.stream().map(item -> item.receivableId().toString()).toList()),
                Map.class);
        assertThat(receipt.getStatusCode()).isEqualTo(HttpStatus.OK);

        LeaseApi.LeaseContractView paid = getContract(contract.id());
        assertThat(paid.schedules().stream().filter(item -> item.receivableId() != null))
                .allMatch(item -> "SETTLED".equals(item.receivableStatus()))
                .allMatch(item -> item.outstanding().signum() == 0);
        LeaseApi.LeaseContractView generatedAgain = post(
                "/api/ivs/lease-contracts/" + contract.id() + "/generate-due?throughDate=2027-02-15",
                Map.of(), LeaseApi.LeaseContractView.class);
        assertThat(generatedAgain.schedules().stream().filter(item -> item.receivableId() != null)).hasSize(2);

        LeaseApi.LeaseContractView ended = post(
                "/api/ivs/lease-contracts/" + contract.id() + "/end", Map.of(
                        "settlementDate", "2027-02-15", "applyDeposit", false,
                        "reason", "测试到期归还"), LeaseApi.LeaseContractView.class);
        assertThat(ended.status()).isEqualTo(LeaseStatus.ENDED);
        assertThat(serialById(fixture.serial().id()).status()).isEqualTo(SerialStatus.IN_STOCK);
        assertThat(serialById(fixture.serial().id()).ownershipType()).isEqualTo(OwnershipType.OWNED);
        assertThat(leaseContractId(fixture.serial().id())).isNull();
        assertThat(balanceOf(fixture.serial()).onHandQty()).isEqualByComparingTo("1.000");
        assertThat(balanceOf(fixture.serial()).availableQty()).isEqualByComparingTo("1.000");
    }

    @Test
    void 整车不能租赁且未支持的到期方式会被拒绝() {
        AssetFixture vehicle = inStockAsset("VEHICLE", true);
        LocalDate start = LocalDate.now().plusDays(1);
        ResponseEntity<Map> vehicleLease = restTemplate.postForEntity("/api/ivs/lease-contracts",
                contractBody(vehicle, start, start.plusMonths(12), new BigDecimal("5000.00"),
                        LeaseOwnershipAtEnd.RETURN), Map.class);
        assertThat(vehicleLease.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(vehicleLease.getBody()).containsEntry("code", "LEASE_SERIAL_NOT_BATTERY");

        AssetFixture battery = inStockAsset("BUYOUT", false);
        LeaseApi.LeaseContractView buyout = createContract(battery, start, start.plusMonths(12),
                new BigDecimal("8000.00"), LeaseOwnershipAtEnd.BUYOUT);
        ResponseEntity<Map> endRejected = restTemplate.postForEntity(
                "/api/ivs/lease-contracts/" + buyout.id() + "/end", Map.of(), Map.class);
        assertThat(endRejected.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(endRejected.getBody()).containsEntry("code", "LEASE_END_MODE_UNSUPPORTED");
        assertThat(serialById(battery.serial().id()).status()).isEqualTo(SerialStatus.IN_LEASE);
    }

    @Test
    void 月租批量自动化生成应收并发送到期逾期提醒且按天去重() {
        AssetFixture fixture = inStockAsset("AUTOMATION", false);
        LocalDate start = LocalDate.of(2026, 12, 1);
        LeaseApi.LeaseContractView contract = createContract(fixture, start, start.plusMonths(3),
                new BigDecimal("12000.00"), LeaseOwnershipAtEnd.RETURN);

        LeaseApi.DueGenerationResult batch = post(
                "/api/ivs/lease-contracts/generate-due-batch?throughDate=2027-02-15", Map.of(),
                LeaseApi.DueGenerationResult.class);
        assertThat(batch.activeContracts()).isEqualTo(1);
        assertThat(batch.generatedReceivables()).isEqualTo(2);

        LeaseApi.AutomationResult first = post(
                "/api/ivs/lease-contracts/run-automation?asOfDate=2027-02-15&reminderDays=14", Map.of(),
                LeaseApi.AutomationResult.class);
        assertThat(first.generatedReceivables()).isZero();
        assertThat(first.overdueReminders()).isEqualTo(2);
        assertThat(first.dueSoonReminders()).isEqualTo(1);

        LeaseApi.LeaseContractView afterAutomation = getContract(contract.id());
        assertThat(afterAutomation.schedules().stream().filter(item -> item.receivableId() != null))
                .hasSize(2)
                .allMatch(item -> "OVERDUE".equals(item.receivableStatus()));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from ivs.user_notification where category = 'LEASE_RENT' and business_id = ?",
                Integer.class, contract.id())).isEqualTo(3);
        List<String> audiences = jdbcTemplate.queryForList(
                "select audience_permission from ivs.user_notification where category = 'LEASE_RENT'",
                String.class);
        assertThat(audiences).isNotEmpty()
                .allMatch(value -> value.contains("IVS_LEASE_MANAGE") && value.contains("IVS_FINANCE_READ"));

        LeaseApi.AutomationResult second = post(
                "/api/ivs/lease-contracts/run-automation?asOfDate=2027-02-15&reminderDays=14", Map.of(),
                LeaseApi.AutomationResult.class);
        assertThat(second.generatedReceivables()).isZero();
        assertThat(second.overdueReminders()).isZero();
        assertThat(second.dueSoonReminders()).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from ivs.lease_reminder_log", Integer.class))
                .isEqualTo(3);
    }

    @Test
    void 押金收取抵扣租金并在提前归还后退还余额() {
        AssetFixture fixture = inStockAsset("DEPOSIT", false);
        LocalDate start = LocalDate.of(2026, 12, 1);
        LeaseApi.LeaseContractView contract = createContract(fixture, start, start.plusMonths(3),
                new BigDecimal("12000.00"), LeaseOwnershipAtEnd.RETURN);

        LeaseApi.LeaseContractView collected = post(
                "/api/ivs/lease-contracts/" + contract.id() + "/deposits/collect", Map.of(
                        "amount", 40000,
                        "transactionDate", "2026-12-01",
                        "method", "BANK_TRANSFER",
                        "remark", "首期押金"), LeaseApi.LeaseContractView.class);
        assertThat(collected.depositReceived()).isEqualByComparingTo("40000.00");
        assertThat(collected.depositAvailable()).isEqualByComparingTo("40000.00");
        assertThat(collected.depositTransactions()).hasSize(1)
                .allMatch(item -> "COLLECT".equals(item.type()));

        ResponseEntity<Map> overCollect = restTemplate.postForEntity(
                "/api/ivs/lease-contracts/" + contract.id() + "/deposits/collect", Map.of(
                        "amount", 10000.01, "transactionDate", "2026-12-01", "method", "BANK_TRANSFER"),
                Map.class);
        assertThat(overCollect.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(overCollect.getBody()).containsEntry("code", "LEASE_DEPOSIT_EXCEEDS_EXPECTED");

        LeaseApi.DueGenerationResult batch = post(
                "/api/ivs/lease-contracts/generate-due-batch?throughDate=2027-02-15", Map.of(),
                LeaseApi.DueGenerationResult.class);
        assertThat(batch.generatedReceivables()).isEqualTo(2);

        LeaseApi.LeaseContractView ended = post(
                "/api/ivs/lease-contracts/" + contract.id() + "/end", Map.of(
                        "settlementDate", "2027-02-15", "applyDeposit", true,
                        "reason", "客户提前归还"), LeaseApi.LeaseContractView.class);
        assertThat(ended.status()).isEqualTo(LeaseStatus.ENDED);
        assertThat(ended.settlement()).isNotNull();
        assertThat(ended.settlement().outstandingBefore()).isEqualByComparingTo("24000.00");
        assertThat(ended.settlement().depositApplied()).isEqualByComparingTo("24000.00");
        assertThat(ended.settlement().outstandingAfter()).isEqualByComparingTo("0.00");
        assertThat(ended.settlement().depositRefundable()).isEqualByComparingTo("16000.00");
        assertThat(ended.depositAvailable()).isEqualByComparingTo("16000.00");
        assertThat(ended.schedules().stream().filter(item -> item.receivableId() != null))
                .hasSize(2).allMatch(item -> "SETTLED".equals(item.receivableStatus()));
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from ivs.receipt where method = 'DEPOSIT_OFFSET'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select sum(amount) from ivs.receipt where method = 'DEPOSIT_OFFSET'", BigDecimal.class))
                .isEqualByComparingTo("24000.00");
        assertThat(serialById(fixture.serial().id()).status()).isEqualTo(SerialStatus.IN_STOCK);

        LeaseApi.LeaseContractView refunded = post(
                "/api/ivs/lease-contracts/" + contract.id() + "/deposits/refund", Map.of(
                        "amount", 16000, "transactionDate", "2027-02-16", "method", "BANK_TRANSFER",
                        "remark", "退还剩余押金"), LeaseApi.LeaseContractView.class);
        assertThat(refunded.depositRefunded()).isEqualByComparingTo("16000.00");
        assertThat(refunded.depositAvailable()).isEqualByComparingTo("0.00");
        assertThat(refunded.depositTransactions()).hasSize(3)
                .extracting(LeaseApi.DepositTransactionView::type)
                .containsExactly("COLLECT", "APPLY_RENT", "REFUND");

        ResponseEntity<Map> overRefund = restTemplate.postForEntity(
                "/api/ivs/lease-contracts/" + contract.id() + "/deposits/refund", Map.of(
                        "amount", 0.01, "transactionDate", "2027-02-16", "method", "BANK_TRANSFER"),
                Map.class);
        assertThat(overRefund.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(overRefund.getBody()).containsEntry("code", "LEASE_DEPOSIT_INSUFFICIENT");
    }

    @Test
    void 电池健康档案汇总到租赁资产经营报表() {
        AssetFixture fixture = inStockAsset("HEALTH", false);
        LocalDate start = LocalDate.of(2026, 12, 1);
        LeaseApi.LeaseContractView contract = createContract(fixture, start, start.plusMonths(3),
                new BigDecimal("12000.00"), LeaseOwnershipAtEnd.RETURN);

        LeaseApi.HealthRecordView normal = post(
                "/api/ivs/lease-contracts/" + contract.id() + "/health-records", Map.of(
                        "recordDate", "2027-01-15", "sohPercent", 96.5, "cycleCount", 100,
                        "mileageKm", 15000, "inspectionResult", "NORMAL", "inspector", "检测员甲",
                        "remark", "状态正常"), LeaseApi.HealthRecordView.class);
        assertThat(normal.sohPercent()).isEqualByComparingTo("96.50");

        LeaseApi.HealthRecordView latest = post(
                "/api/ivs/lease-contracts/" + contract.id() + "/health-records", Map.of(
                        "recordDate", "2027-02-15", "sohPercent", 90.25, "cycleCount", 130,
                        "mileageKm", 22000, "inspectionResult", "ATTENTION", "inspector", "检测员乙",
                        "remark", "建议缩短检测周期"), LeaseApi.HealthRecordView.class);
        assertThat(latest.inspectionResult()).isEqualTo("ATTENTION");

        ResponseEntity<Map> invalid = restTemplate.postForEntity(
                "/api/ivs/lease-contracts/" + contract.id() + "/health-records", Map.of(
                        "recordDate", "2027-02-16", "sohPercent", 101, "cycleCount", 131,
                        "mileageKm", 22500, "inspectionResult", "NORMAL"), Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(invalid.getBody()).containsEntry("code", "LEASE_HEALTH_INVALID");

        LeaseApi.HealthRecordView[] records = restTemplate.getForEntity(
                "/api/ivs/lease-contracts/" + contract.id() + "/health-records",
                LeaseApi.HealthRecordView[].class).getBody();
        assertThat(records).isNotNull().hasSize(2);
        assertThat(records[0].recordDate()).isEqualTo(LocalDate.of(2027, 2, 15));

        LeaseApi.DueGenerationResult batch = post(
                "/api/ivs/lease-contracts/generate-due-batch?throughDate=2027-02-15", Map.of(),
                LeaseApi.DueGenerationResult.class);
        assertThat(batch.generatedReceivables()).isEqualTo(2);
        List<String> receivableIds = getContract(contract.id()).schedules().stream()
                .filter(item -> item.receivableId() != null).map(item -> item.receivableId().toString()).toList();
        ResponseEntity<Map> receipt = restTemplate.postForEntity("/api/ivs/receipts", Map.of(
                "customerId", fixture.customer().id().toString(), "amount", 10000,
                "method", PaymentMethod.BANK_TRANSFER.name(), "receivableIds", receivableIds), Map.class);
        assertThat(receipt.getStatusCode()).isEqualTo(HttpStatus.OK);

        LeaseApi.LeaseAssetReportView report = restTemplate.getForEntity(
                "/api/ivs/lease-assets/report", LeaseApi.LeaseAssetReportView.class).getBody();
        assertThat(report).isNotNull();
        assertThat(report.generatedAt()).isNotNull();
        assertThat(report.summary().totalContracts()).isEqualTo(1);
        assertThat(report.summary().activeContracts()).isEqualTo(1);
        assertThat(report.summary().attentionAssets()).isEqualTo(1);
        assertThat(report.summary().activeMonthlyRent()).isEqualByComparingTo("12000.00");
        assertThat(report.summary().generatedRent()).isEqualByComparingTo("24000.00");
        assertThat(report.summary().receivedRent()).isEqualByComparingTo("10000.00");
        assertThat(report.summary().outstandingRent()).isEqualByComparingTo("14000.00");
        assertThat(report.summary().depositAvailable()).isEqualByComparingTo("0.00");
        assertThat(report.rows()).hasSize(1);
        LeaseApi.LeaseAssetReportRow row = report.rows().get(0);
        assertThat(row.contractNo()).isEqualTo(contract.contractNo());
        assertThat(row.customerName()).isEqualTo(fixture.customer().name());
        assertThat(row.serialNo()).isEqualTo(fixture.serial().serialNo());
        assertThat(row.latestSohPercent()).isEqualByComparingTo("90.25");
        assertThat(row.latestCycleCount()).isEqualTo(130);
        assertThat(row.latestMileageKm()).isEqualByComparingTo("22000.00");
        assertThat(row.latestInspectionResult()).isEqualTo("ATTENTION");
        assertThat(row.latestInspectionDate()).isEqualTo(LocalDate.of(2027, 2, 15));
        assertThat(row.receivedRent()).isEqualByComparingTo("10000.00");
        assertThat(row.outstandingRent()).isEqualByComparingTo("14000.00");
    }

    private LeaseApi.LeaseContractView createContract(AssetFixture fixture, LocalDate start, LocalDate end,
                                                       BigDecimal monthlyFee, LeaseOwnershipAtEnd ownershipAtEnd) {
        return post("/api/ivs/lease-contracts", contractBody(fixture, start, end, monthlyFee, ownershipAtEnd),
                LeaseApi.LeaseContractView.class);
    }

    private Map<String, Object> contractBody(AssetFixture fixture, LocalDate start, LocalDate end,
                                             BigDecimal monthlyFee, LeaseOwnershipAtEnd ownershipAtEnd) {
        return Map.of(
                "customerId", fixture.customer().id().toString(),
                "serialId", fixture.serial().id().toString(),
                "startDate", start.toString(),
                "endDate", end.toString(),
                "monthlyFee", monthlyFee,
                "deposit", new BigDecimal("50000.00"),
                "ownershipAtEnd", ownershipAtEnd.name(),
                "remark", "二期租赁验收");
    }

    private AssetFixture inStockAsset(String prefix, boolean vehicle) {
        String template = vehicle ? "VEHICLE" : "BATTERY";
        String categoryCode = vehicle ? "VEHICLE" : "BATTERY";
        String categoryName = vehicle ? "整车" : "动力电池";
        MasterdataApi.CategoryView category = post("/api/ivs/product-categories", Map.of(
                "code", categoryCode, "name", categoryName, "trackingMode", TrackingMode.SERIAL.name(),
                "statusTemplate", template, "unit", vehicle ? "台" : "组", "sortOrder", 0),
                MasterdataApi.CategoryView.class);
        Map<String, Object> productBody = new HashMap<>();
        productBody.put("name", vehicle ? "Q3 牵引车" : "282kWh 动力电池");
        productBody.put("categoryId", category.id().toString());
        productBody.put("unit", vehicle ? "台" : "组");
        productBody.put("series", vehicle ? "Q3" : "282kWh");
        productBody.put("model", vehicle ? "T31" : "B282");
        productBody.put("minPrice", vehicle ? 300000 : 100000);
        productBody.put("salePriceGuide", vehicle ? 400000 : 320000);
        MasterdataApi.ProductView product = post("/api/ivs/products", productBody, MasterdataApi.ProductView.class);
        MasterdataApi.WarehouseView warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "WH-" + prefix, "name", prefix + "仓", "type", WarehouseType.MAIN.name()),
                MasterdataApi.WarehouseView.class);
        MasterdataApi.SupplierView supplier = post("/api/ivs/suppliers", Map.of(
                "name", prefix + "供应商", "supplierType", SupplierType.OEM.name()), MasterdataApi.SupplierView.class);
        MasterdataApi.CustomerView customer = post("/api/ivs/customers", Map.of(
                "name", prefix + "客户", "customerType", CustomerType.COMPANY.name(), "creditLimit", 5000000),
                MasterdataApi.CustomerView.class);
        String serialNo = prefix + "-SN-001";
        MasterdataApi.SerialView serial = post("/api/ivs/product-serials", Map.of(
                "serialNo", serialNo, "productId", product.id().toString()), MasterdataApi.SerialView.class);

        Map<String, Object> line = new HashMap<>();
        line.put("productId", product.id().toString());
        line.put("quantity", 1);
        line.put("unitPrice", vehicle ? 420000 : 300000);
        if (!vehicle) {
            line.put("batteryBoxManufacturer", "电池箱厂 A");
            line.put("batteryBoxModel", "BOX-282");
            line.put("batteryBoxUnitPrice", 100000);
            line.put("batteryPackManufacturer", "电池包厂 B");
            line.put("batteryEnergyKwh", new BigDecimal("282.000"));
            line.put("batteryPackUnitPrice", 200000);
            line.put("arrivalDate", LocalDate.now().plusDays(7).toString());
        }
        ResponseEntity<ProcurementApi.PurchaseOrderView> purchaseResponse = restTemplate.postForEntity(
                "/api/ivs/purchase-orders", Map.of(
                "supplierId", supplier.id().toString(), "warehouseId", warehouse.id().toString(),
                "externalOrderNo", prefix + "-PO-001", "lines", List.of(line)),
                ProcurementApi.PurchaseOrderView.class);
        assertThat(purchaseResponse.getStatusCode()).as("采购单创建响应: %s", purchaseResponse.getBody())
                .isEqualTo(HttpStatus.CREATED);
        ProcurementApi.PurchaseOrderView purchase = purchaseResponse.getBody();
        ResponseEntity<ProcurementApi.PurchaseOrderView> approved = restTemplate.postForEntity(
                "/api/ivs/purchase-orders/" + purchase.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        assertThat(approved.getStatusCode()).as("采购单审核响应: %s", approved.getBody()).isEqualTo(HttpStatus.OK);
        purchase = approved.getBody();
        ResponseEntity<ProcurementApi.PurchaseReceiptView> received = restTemplate.postForEntity(
                "/api/ivs/purchase-orders/" + purchase.id() + "/receive", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of(
                        "orderLineId", purchase.lines().get(0).id().toString(),
                        "quantity", 1,
                        "serialNos", List.of(serialNo),
                        "unitPrice", vehicle ? 420000 : 300000))),
                ProcurementApi.PurchaseReceiptView.class);
        assertThat(received.getStatusCode()).as("采购收货响应: %s", received.getBody()).isEqualTo(HttpStatus.OK);
        MasterdataApi.SerialView inStock = serialById(serial.id());
        assertThat(inStock.status()).isEqualTo(SerialStatus.IN_STOCK);
        return new AssetFixture(product, customer, inStock);
    }

    private MasterdataApi.SerialView serialById(UUID id) {
        return restTemplate.getForEntity("/api/ivs/product-serials/" + id, MasterdataApi.SerialView.class).getBody();
    }

    private LeaseApi.LeaseContractView getContract(UUID id) {
        return restTemplate.getForEntity("/api/ivs/lease-contracts/" + id, LeaseApi.LeaseContractView.class).getBody();
    }

    private UUID leaseContractId(UUID serialId) {
        return jdbcTemplate.queryForObject(
                "select lease_contract_id from ivs.product_serial where id = ?", UUID.class, serialId);
    }

    private InventoryApi.BalanceView balanceOf(MasterdataApi.SerialView serial) {
        InventoryApi.BalanceView[] balances = restTemplate.getForEntity(
                "/api/ivs/inventory/balances?warehouseId=" + serial.warehouseId(),
                InventoryApi.BalanceView[].class).getBody();
        assertThat(balances).isNotNull();
        return java.util.Arrays.stream(balances)
                .filter(item -> item.productId().equals(serial.productId()))
                .findFirst().orElseThrow();
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }

    private record AssetFixture(MasterdataApi.ProductView product,
                                MasterdataApi.CustomerView customer,
                                MasterdataApi.SerialView serial) {
    }
}
