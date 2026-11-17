package com.ivs.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.billing.purchase.api.PurchaseInvoiceApi;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.policy.claim.api.ClaimApi;
import com.ivs.policy.fund.api.RebateFundApi;
import com.ivs.policy.policy.api.EntitlementApi;
import com.ivs.policy.policy.api.PolicyApi;
import com.ivs.policy.report.PolicyReportApi;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M4-3b：实销后申请兑现 → 申报单 → 厂家确认/差异 → 红字入池 → 政策成本报表。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ClaimWorkflowIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 权益归集申报_厂家差异确认_红字结算入池并更新政策成本() {
        UUID supplierId = createSupplier("某整车厂 A");
        PolicyApi.PolicyView policy = publish(createPolicy(supplierId, "阶段性补贴", "10000", "100000", "MONTHLY"));
        VehicleFixture vehicle = createVehicleFixture(supplierId);
        UUID orderId = vehicle.purchaseOrderId();
        UUID retailId = vehicle.retailId();
        applyPre(orderId, UUID.randomUUID(), UUID.randomUUID(), List.of(policy.id()));

        EntitlementApi[] entitlements = generate(orderId, retailId);
        assertThat(entitlements).hasSize(1);
        assertThat(entitlements[0].supplierId()).isEqualTo(supplierId);

        List<ClaimApi.ClaimView> claims = applyClaims(List.of(entitlements[0].id()), false);
        assertThat(claims).hasSize(1);
        ClaimApi.ClaimView claim = claims.get(0);
        assertThat(claim.claimNo()).startsWith("CLM-");
        assertThat(claim.supplierName()).isEqualTo("某整车厂 A");
        assertThat(claim.policyName()).isEqualTo("阶段性补贴");
        assertThat(claim.claimAmount()).isEqualByComparingTo("10000.00");
        assertThat(claim.periodCode()).isEqualTo("2026-10");
        assertThat(claim.status()).isEqualTo("DRAFT");
        assertThat(claim.lines()).hasSize(1);
        assertThat(claim.lines().get(0).retailDate()).isEqualTo(LocalDate.of(2026, 10, 15));

        ClaimApi.ClaimView submitted = post("/api/ivs/oem-claims/" + claim.id() + "/submit", Map.of(),
                ClaimApi.ClaimView.class);
        assertThat(submitted.status()).isEqualTo("SUBMITTED");

        UUID lineId = claim.lines().get(0).id();
        Map<String, Object> confirmBody = Map.of(
                "lines", List.of(Map.of("lineId", lineId.toString(), "confirmedAmount", 9500,
                        "reason", "厂家按政策口径核减", "disposition", "SELF_BORNE")),
                "disputed", false);
        ClaimApi.ClaimView confirmed = post("/api/ivs/oem-claims/" + claim.id() + "/confirm", confirmBody,
                ClaimApi.ClaimView.class);
        assertThat(confirmed.status()).isEqualTo("PARTIALLY_CONFIRMED");
        assertThat(confirmed.confirmedAmount()).isEqualByComparingTo("9500.00");
        assertThat(confirmed.differenceAmount()).isEqualByComparingTo("500.00");
        assertThat(confirmed.differences()).hasSize(1);
        assertThat(confirmed.differences().get(0).reason()).isEqualTo("厂家按政策口径核减");
        assertThat(confirmed.differences().get(0).disposition()).isEqualTo("SELF_BORNE");
        assertThat(confirmed.lines().get(0).dispositionTime()).isNotNull();

        Map<String, Object> mismatchBody = new HashMap<>();
        mismatchBody.put("supplierId", supplierId.toString());
        mismatchBody.put("amount", 9500);
        mismatchBody.put("claimId", claim.id().toString());
        mismatchBody.put("serialNos", List.of("LZZWRONG0000000001"));
        var mismatch = restTemplate.postForEntity("/api/ivs/purchase-invoices/red", mismatchBody, Map.class);
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(mismatch.getBody().get("code")).isEqualTo("CLAIM_SERIAL_MISMATCH");

        Map<String, Object> redBody = new HashMap<>();
        redBody.put("supplierId", supplierId.toString());
        redBody.put("amount", 9500);
        redBody.put("taxRate", 0.13);
        redBody.put("externalInvoiceNo", "RED-CLAIM-001");
        redBody.put("claimId", claim.id().toString());
        redBody.put("serialNos", List.of(vehicle.serialNo()));
        PurchaseInvoiceApi.PurchaseInvoiceView red = post("/api/ivs/purchase-invoices/red", redBody,
                PurchaseInvoiceApi.PurchaseInvoiceView.class);
        assertThat(red.claimId()).isEqualTo(claim.id());
        assertThat(red.serialNos()).containsExactly(vehicle.serialNo());
        assertThat(red.payableAppliedAmount()).isEqualByComparingTo("9500.00");
        assertThat(red.fundPoolAmount()).isEqualByComparingTo("0.00");

        PurchaseInvoiceApi.PurchaseInvoiceView[] rebateRedInvoices = get(
                "/api/ivs/rebate-offset-workspace/red-invoices?supplierId=" + supplierId,
                PurchaseInvoiceApi.PurchaseInvoiceView[].class);
        assertThat(rebateRedInvoices).hasSize(1);
        assertThat(rebateRedInvoices[0].invoiceNo()).isEqualTo(red.invoiceNo());
        assertThat(rebateRedInvoices[0].payableAppliedAmount()).isEqualByComparingTo("9500.00");
        assertThat(rebateRedInvoices[0].fundPoolAmount()).isEqualByComparingTo("0.00");

        com.ivs.procurement.payable.api.PayableApi.PayableView[] payables = get(
                "/api/ivs/payables?supplierId=" + supplierId,
                com.ivs.procurement.payable.api.PayableApi.PayableView[].class);
        assertThat(payables).hasSize(1);
        assertThat(payables[0].amount()).isEqualByComparingTo("420000.00");
        assertThat(payables[0].rebateCreditAmount()).isEqualByComparingTo("9500.00");
        assertThat(payables[0].netAmount()).isEqualByComparingTo("410500.00");
        assertThat(payables[0].outstanding()).isEqualByComparingTo("410500.00");

        ClaimApi.ClaimView settled = get("/api/ivs/oem-claims/" + claim.id(), ClaimApi.ClaimView.class);
        assertThat(settled.status()).isEqualTo("SETTLED");
        assertThat(settled.invoiceNo()).isEqualTo(red.invoiceNo());

        RebateFundApi.PoolView pool = get("/api/ivs/rebate-fund-pool", RebateFundApi.PoolView.class);
        assertThat(pool.balanceAmount()).isEqualByComparingTo("0.00");

        EntitlementApi entitlement = get("/api/ivs/entitlements?retailId=" + retailId,
                EntitlementApi[].class)[0];
        assertThat(entitlement.status()).isEqualTo("SETTLED");
        assertThat(entitlement.confirmedAmount()).isEqualByComparingTo("9500.00");
        assertThat(entitlement.settledAmount()).isEqualByComparingTo("9500.00");

        PolicyReportApi.UnitCostRow[] unitCosts = get("/api/ivs/policies/reports/unit-cost",
                PolicyReportApi.UnitCostRow[].class);
        assertThat(unitCosts).hasSize(1);
        assertThat(unitCosts[0].claimedAmount()).isEqualByComparingTo("10000.00");
        assertThat(unitCosts[0].confirmedAmount()).isEqualByComparingTo("9500.00");
        assertThat(unitCosts[0].settledAmount()).isEqualByComparingTo("9500.00");
        assertThat(unitCosts[0].claimNo()).isEqualTo(claim.claimNo());
        assertThat(unitCosts[0].redInvoiceNo()).isEqualTo(red.invoiceNo());

        PolicyReportApi.RebateSummaryRow[] summaries = get("/api/ivs/policies/reports/rebate-summary",
                PolicyReportApi.RebateSummaryRow[].class);
        assertThat(summaries).hasSize(1);
        assertThat(summaries[0].faceAmount()).isEqualByComparingTo("10000.00");
        assertThat(summaries[0].confirmedAmount()).isEqualByComparingTo("9500.00");
        assertThat(summaries[0].settledAmount()).isEqualByComparingTo("9500.00");

        PolicyReportApi.ClaimDifferenceRow[] differences = get(
                "/api/ivs/policies/reports/claim-differences", PolicyReportApi.ClaimDifferenceRow[].class);
        assertThat(differences).hasSize(1);
        assertThat(differences[0].amount()).isEqualByComparingTo("500.00");
        assertThat(differences[0].claimNo()).isEqualTo(claim.claimNo());
    }

    @Test
    void 同厂家同周期可合并申报且保留政策明细() {
        UUID supplierId = createSupplier("某整车厂 B");
        PolicyApi.PolicyView a = publish(createPolicy(supplierId, "建店补贴", "10000", "100000", "MONTHLY"));
        PolicyApi.PolicyView b = publish(createPolicy(supplierId, "库存车补贴", "3000", "100000", "MONTHLY"));
        UUID orderId = UUID.randomUUID();
        UUID retailId = createRetail(new BigDecimal("450000"));
        applyPre(orderId, UUID.randomUUID(), UUID.randomUUID(), List.of(a.id(), b.id()));

        EntitlementApi[] entitlements = generate(orderId, retailId);
        assertThat(entitlements).hasSize(2);
        List<ClaimApi.ClaimView> claims = applyClaims(
                List.of(entitlements[0].id(), entitlements[1].id()), true);
        assertThat(claims).hasSize(1);
        assertThat(claims.get(0).mergedClaim()).isTrue();
        assertThat(claims.get(0).claimAmount()).isEqualByComparingTo("13000.00");
        assertThat(claims.get(0).lines()).hasSize(2);
        assertThat(claims.get(0).lines()).extracting(ClaimApi.ClaimLineView::policyName)
                .containsExactlyInAnyOrder("建店补贴", "库存车补贴");

        // 差异必须带原因与处置
        post("/api/ivs/oem-claims/" + claims.get(0).id() + "/submit", Map.of(), ClaimApi.ClaimView.class);
        Map<String, Object> invalidConfirm = Map.of("lines", claims.get(0).lines().stream()
                .map(line -> Map.of("lineId", line.id().toString(), "confirmedAmount", 12000))
                .toList(), "disputed", false);
        var response = restTemplate.postForEntity("/api/ivs/oem-claims/" + claims.get(0).id() + "/confirm",
                invalidConfirm, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("CLAIM_CONFIRM_INVALID");
    }

    private VehicleFixture createVehicleFixture(UUID supplierId) {
        String code = "CLAIM-" + UUID.randomUUID().toString().substring(0, 8);
        MasterdataApi.CategoryView category = post("/api/ivs/product-categories", Map.of(
                "code", code, "name", "申报红字整车", "trackingMode", TrackingMode.SERIAL.name(),
                "statusTemplate", "VEHICLE", "unit", "台", "sortOrder", 0), MasterdataApi.CategoryView.class);
        MasterdataApi.ProductView product = post("/api/ivs/products", Map.of(
                "name", "申报红字整车", "categoryId", category.id().toString(), "unit", "台",
                "minPrice", 300000, "salePriceGuide", 450000), MasterdataApi.ProductView.class);
        MasterdataApi.WarehouseView warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "WH-" + code, "name", "申报红字仓", "type", "MAIN"), MasterdataApi.WarehouseView.class);
        String serialNo = "LZZCLAIM" + UUID.randomUUID().toString().replace("-", "").substring(0, 9).toUpperCase();
        MasterdataApi.SerialView serial = post("/api/ivs/product-serials", Map.of(
                "serialNo", serialNo, "productId", product.id().toString()), MasterdataApi.SerialView.class);
        ProcurementApi.PurchaseOrderView order = post("/api/ivs/purchase-orders", Map.of(
                "supplierId", supplierId.toString(), "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1,
                        "unitPrice", 420000))), ProcurementApi.PurchaseOrderView.class);
        order = post("/api/ivs/purchase-orders/" + order.id() + "/approve", Map.of(),
                ProcurementApi.PurchaseOrderView.class);
        post("/api/ivs/purchase-orders/" + order.id() + "/receive", Map.of(
                "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("orderLineId", order.lines().get(0).id().toString(),
                        "serialNos", List.of(serial.serialNo())))), ProcurementApi.PurchaseReceiptView.class);
        com.ivs.retail.api.RetailApi.RetailView retail = post("/api/ivs/retails", Map.of(
                "retailDate", "2026-10-15", "invoiceIssuedAt", "2026-10-15T02:00:00Z",
                "serialId", serial.id().toString(), "userName", "政策红字测试用户",
                "userMobile", "13800000000", "salePrice", 450000),
                com.ivs.retail.api.RetailApi.RetailView.class);
        return new VehicleFixture(order.id(), retail.id(), serial.serialNo());
    }

    private record VehicleFixture(UUID purchaseOrderId, UUID retailId, String serialNo) {
    }

    private UUID createSupplier(String name) {
        return post("/api/ivs/suppliers", Map.of("name", name, "supplierType", SupplierType.VEHICLE_OEM.name()),
                MasterdataApi.SupplierView.class).id();
    }

    private PolicyApi.PolicyView createPolicy(UUID supplierId, String name, String value, String total,
                                              String cycle) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("policyType", "PHASE_SUBSIDY");
        body.put("categoryId", null);
        body.put("supplierId", supplierId.toString());
        body.put("benefitType", "AMOUNT");
        body.put("benefitValue", new BigDecimal(value));
        body.put("effFrom", LocalDate.now().minusDays(1).toString());
        body.put("effTo", LocalDate.now().plusDays(30).toString());
        body.put("referenceDateType", "RETAIL_DATE");
        body.put("claimCycle", cycle);
        body.put("claimCutoffDay", 5);
        body.put("stackingRule", "ADDITIVE");
        body.put("limitType", "AMOUNT");
        body.put("totalAmount", new BigDecimal(total));
        return post("/api/ivs/policies", body, PolicyApi.PolicyView.class);
    }

    private PolicyApi.PolicyView publish(PolicyApi.PolicyView policy) {
        return post("/api/ivs/policies/" + policy.id() + "/publish", Map.of(), PolicyApi.PolicyView.class);
    }

    private void applyPre(UUID orderId, UUID lineId, UUID productId, List<UUID> policyIds) {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId.toString());
        body.put("orderLineId", lineId.toString());
        body.put("productId", productId.toString());
        body.put("orderLineAmount", 450000);
        body.put("policyIds", policyIds.stream().map(UUID::toString).toList());
        var response = restTemplate.postForEntity("/api/ivs/pre-entitlements", body,
                PolicyApi.PreEntitlementView[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private EntitlementApi[] generate(UUID orderId, UUID retailId) {
        return post("/api/ivs/entitlements/generate?orderId=" + orderId + "&retailId=" + retailId
                + "&retailDate=2026-10-15&serialId=" + UUID.randomUUID(), Map.of(), EntitlementApi[].class);
    }

    private List<ClaimApi.ClaimView> applyClaims(List<UUID> entitlementIds, boolean merge) {
        Map<String, Object> body = Map.of("entitlementIds",
                entitlementIds.stream().map(UUID::toString).toList(), "merge", merge);
        var raw = restTemplate.postForEntity("/api/ivs/entitlements/apply", body, String.class);
        assertThat(raw.getStatusCode()).as("apply response: %s", raw.getBody()).isEqualTo(HttpStatus.OK);
        try {
            return List.of(objectMapper.readValue(raw.getBody(), ClaimApi.ClaimView[].class));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
    private UUID createRetail(BigDecimal salePrice) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update("insert into ivs.retail_record (id, retail_no, retail_date, invoice_issued_at, "
                        + "serial_id, serial_no, product_id, user_name, user_mobile, sale_price, status, "
                        + "active_serial_key, created_at, updated_at, version) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                id, "RT-TEST-" + id.toString().substring(0, 8), LocalDate.of(2026, 10, 15), now,
                UUID.randomUUID(), "VIN-" + id.toString().substring(0, 8), UUID.randomUUID(),
                "测试用户", "13800000000", salePrice, "VERIFIED", UUID.randomUUID(), now, now);
        return id;
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }

    private <T> T get(String url, Class<T> type) {
        return restTemplate.getForEntity(url, type).getBody();
    }
}




