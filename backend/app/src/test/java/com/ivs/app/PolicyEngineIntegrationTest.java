package com.ivs.app;

import com.ivs.policy.policy.api.PolicyApi;
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

/** M4 政策引擎验收：政策录入、额度池（时间/金额/条件）、多政策累加、预享受与释放。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PolicyEngineIntegrationTest {

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
    void 政策录入发布并生成额度池() {
        PolicyApi.PolicyView policy = createPolicy("10 月阶段性补贴", "AMOUNT", new BigDecimal("10000"),
                new BigDecimal("50000"), LocalDate.now().minusDays(1), LocalDate.now().plusDays(30));
        assertThat(policy.policyNo()).startsWith("POL-");
        assertThat(policy.status().name()).isEqualTo("DRAFT");
        assertThat(policy.pool().remainingAmount()).isEqualByComparingTo("50000.00");

        PolicyApi.PolicyView published = post("/api/ivs/policies/" + policy.id() + "/publish", Map.of(),
                PolicyApi.PolicyView.class);
        assertThat(published.status().name()).isEqualTo("PUBLISHED");
    }

    @Test
    void 同一订单多政策累加且金额分别记账() {
        PolicyApi.PolicyView a = publish(createPolicy("阶段性补贴", "AMOUNT", new BigDecimal("10000"),
                new BigDecimal("50000"), LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));
        PolicyApi.PolicyView b = publish(createPolicy("库存车补贴", "AMOUNT", new BigDecimal("3000"),
                new BigDecimal("30000"), LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));

        List<PolicyApi.PreEntitlementView> pres = apply(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("450000"), List.of(a.id(), b.id()), false, null);
        assertThat(pres).hasSize(2);
        assertThat(pres).extracting(PolicyApi.PreEntitlementView::estimateAmount)
                .containsExactlyInAnyOrder(new BigDecimal("10000.00"), new BigDecimal("3000.00"));

        PolicyApi.PolicyView refreshed = get("/api/ivs/policies/" + a.id(), PolicyApi.PolicyView.class);
        assertThat(refreshed.pool().remainingAmount()).isEqualByComparingTo("40000.00");
    }

    @Test
    void 额度池返完为止且用尽后不再生成预享受() {
        PolicyApi.PolicyView policy = publish(createPolicy("建店补贴", "AMOUNT", new BigDecimal("10000"),
                new BigDecimal("25000"), LocalDate.now().minusDays(1), LocalDate.now().plusDays(30)));

        BigDecimal first = apply(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("450000"), List.of(policy.id()), false, null).get(0).estimateAmount();
        BigDecimal second = apply(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("450000"), List.of(policy.id()), false, null).get(0).estimateAmount();
        BigDecimal third = apply(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("450000"), List.of(policy.id()), false, null).get(0).estimateAmount();
        assertThat(first).isEqualByComparingTo("10000.00");
        assertThat(second).isEqualByComparingTo("10000.00");
        assertThat(third).isEqualByComparingTo("5000.00"); // 返完为止，最后一台拿剩余

        ResponseEntity<Map> exhausted = postRaw(new HashMap<>(Map.of(
                "orderId", UUID.randomUUID().toString(),
                "orderLineId", UUID.randomUUID().toString(),
                "productId", UUID.randomUUID().toString(),
                "orderLineAmount", 450000,
                "policyIds", List.of(policy.id().toString()))));
        assertThat(exhausted.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(exhausted.getBody().get("code")).isEqualTo("NO_POLICY_APPLIED");
    }

    @Test
    void 条件类政策需标记达成_取消预享受释放额度() {
        PolicyApi.PolicyView policy = createPolicy("达成季度目标后返", "AMOUNT", new BigDecimal("8000"),
                new BigDecimal("40000"), LocalDate.now().minusDays(1), LocalDate.now().plusDays(30),
                "CONDITION", "季度销量达成 300 台");
        publish(policy);

        ResponseEntity<Map> before = postRaw(new HashMap<>(Map.of(
                "orderId", UUID.randomUUID().toString(),
                "orderLineId", UUID.randomUUID().toString(),
                "productId", UUID.randomUUID().toString(),
                "orderLineAmount", 450000,
                "policyIds", List.of(policy.id().toString()))));
        assertThat(before.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(before.getBody().get("code")).isEqualTo("POOL_CONDITION_NOT_MET");

        post("/api/ivs/policies/" + policy.id() + "/condition-met", Map.of(), PolicyApi.PolicyView.class);
        List<PolicyApi.PreEntitlementView> pres = apply(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                new BigDecimal("450000"), List.of(policy.id()), true, "订单后补挂政策");
        assertThat(pres).hasSize(1);
        assertThat(pres.get(0).addedAfterOrder()).isTrue();
        assertThat(pres.get(0).addReason()).isEqualTo("订单后补挂政策");

        // 取消预享受 → 额度恢复
        post("/api/ivs/pre-entitlements/" + pres.get(0).id() + "/cancel", Map.of(),
                PolicyApi.PreEntitlementView.class);
        PolicyApi.PolicyView refreshed = get("/api/ivs/policies/" + policy.id(), PolicyApi.PolicyView.class);
        assertThat(refreshed.pool().remainingAmount()).isEqualByComparingTo("40000.00");
    }

    @Test
    void 过期政策不参与匹配() {
        PolicyApi.PolicyView expired = publish(createPolicy("过期政策", "AMOUNT", new BigDecimal("5000"),
                new BigDecimal("50000"), LocalDate.now().minusDays(30), LocalDate.now().minusDays(1)));
        ResponseEntity<Map> response = postRaw(new HashMap<>(Map.of(
                "orderId", UUID.randomUUID().toString(),
                "orderLineId", UUID.randomUUID().toString(),
                "productId", UUID.randomUUID().toString(),
                "orderLineAmount", 450000,
                "policyIds", List.of(expired.id().toString()))));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("NO_POLICY_AVAILABLE");
    }

    /* ===================== 辅助 ===================== */

    private PolicyApi.PolicyView createPolicy(String name, String benefitType, BigDecimal value,
                                              BigDecimal totalAmount, LocalDate from, LocalDate to) {
        return createPolicy(name, benefitType, value, totalAmount, from, to, "AMOUNT", null);
    }

    private PolicyApi.PolicyView createPolicy(String name, String benefitType, BigDecimal value,
                                              BigDecimal totalAmount, LocalDate from, LocalDate to,
                                              String limitType, String conditionDesc) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("policyType", "PHASE_SUBSIDY");
        body.put("benefitType", benefitType);
        body.put("benefitValue", value);
        body.put("effFrom", from.toString());
        body.put("effTo", to.toString());
        body.put("referenceDateType", "RETAIL_DATE");
        body.put("claimCycle", "MONTHLY");
        body.put("claimCutoffDay", 5);
        body.put("priority", 0);
        body.put("stackingRule", "ADDITIVE");
        body.put("limitType", limitType);
        body.put("totalAmount", totalAmount);
        if (conditionDesc != null) body.put("conditionDesc", conditionDesc);
        return post("/api/ivs/policies", body, PolicyApi.PolicyView.class);
    }

    private PolicyApi.PolicyView publish(PolicyApi.PolicyView policy) {
        return post("/api/ivs/policies/" + policy.id() + "/publish", Map.of(), PolicyApi.PolicyView.class);
    }

    private List<PolicyApi.PreEntitlementView> apply(UUID orderId, UUID orderLineId, UUID productId,
                                                     BigDecimal orderLineAmount, List<UUID> policyIds,
                                                     boolean addedAfterOrder, String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put("orderId", orderId.toString());
        body.put("orderLineId", orderLineId.toString());
        body.put("productId", productId.toString());
        body.put("orderLineAmount", orderLineAmount);
        body.put("policyIds", policyIds.stream().map(UUID::toString).toList());
        body.put("addedAfterOrder", addedAfterOrder);
        if (reason != null) body.put("reason", reason);
        ResponseEntity<PolicyApi.PreEntitlementView[]> response = restTemplate.postForEntity(
                "/api/ivs/pre-entitlements", body, PolicyApi.PreEntitlementView[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return List.of(response.getBody());
    }

    private ResponseEntity<Map> postRaw(Map<String, Object> body) {
        return restTemplate.postForEntity("/api/ivs/pre-entitlements", body, Map.class);
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }

    private <T> T get(String url, Class<T> type) {
        return restTemplate.getForEntity(url, type).getBody();
    }
}