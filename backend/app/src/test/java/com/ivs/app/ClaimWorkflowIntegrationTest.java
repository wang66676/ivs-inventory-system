package com.ivs.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ivs.billing.purchase.api.PurchaseInvoiceApi;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.policy.claim.api.ClaimApi;
import com.ivs.policy.fund.api.RebateFundApi;
import com.ivs.policy.policy.api.EntitlementApi;
import com.ivs.policy.policy.api.PolicyApi;
import com.ivs.policy.report.PolicyReportApi;
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
        UUID orderId = UUID.randomUUID();
        UUID retailId = createRetail(new BigDecimal("450000"));
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

        Map<String, Object> redBody = new HashMap<>();
        redBody.put("supplierId", supplierId.toString());
        redBody.put("amount", 9500);
        redBody.put("taxRate", 0.13);
        redBody.put("externalInvoiceNo", "RED-CLAIM-001");
        redBody.put("claimId", claim.id().toString());
        PurchaseInvoiceApi.PurchaseInvoiceView red = post("/api/ivs/purchase-invoices/red", redBody,
                PurchaseInvoiceApi.PurchaseInvoiceView.class);
        assertThat(red.claimId()).isEqualTo(claim.id());

        ClaimApi.ClaimView settled = get("/api/ivs/oem-claims/" + claim.id(), ClaimApi.ClaimView.class);
        assertThat(settled.status()).isEqualTo("SETTLED");
        assertThat(settled.invoiceNo()).isEqualTo(red.invoiceNo());

        RebateFundApi.PoolView pool = get("/api/ivs/rebate-fund-pool", RebateFundApi.PoolView.class);
        assertThat(pool.balanceAmount()).isEqualByComparingTo("9500.00");

        EntitlementApi entitlement = get("/api/ivs/entitlements?retailId=" + retailId,
                EntitlementApi[].class)[0];
        assertThat(entitlement.status()).isEqualTo("SETTLED");
        assertThat(entitlement.confirmedAmount()).isEqualByComparingTo("9500.00");
        assertThat(entitlement.settledAmount()).isEqualByComparingTo("9500.00");

        PolicyReportApi.UnitCostRow[] unitCosts = get("/api/ivs/policies/reports/unit-cost",
                PolicyReportApi.UnitCostRow[].class);
        assertThat(unitCosts).hasSize(1);
        assertThat(unitCosts[0].salePrice()).isEqualByComparingTo("450000.00");
        assertThat(unitCosts[0].policyCost()).isEqualByComparingTo("9500.00");
        assertThat(unitCosts[0].netPrice()).isEqualByComparingTo("440500.00");

        PolicyReportApi.CostRateRow[] rates = get("/api/ivs/policies/reports/cost-rate",
                PolicyReportApi.CostRateRow[].class);
        assertThat(rates).hasSize(1);
        assertThat(rates[0].costRate()).isEqualByComparingTo("2.11");

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

    private UUID createSupplier(String name) {
        return post("/api/ivs/suppliers", Map.of("name", name, "supplierType", SupplierType.OEM.name()),
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




