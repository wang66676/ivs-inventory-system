package com.ivs.app;

import com.ivs.policy.policy.api.EntitlementApi;
import com.ivs.policy.policy.api.PolicyApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 实销确认后：预享受项转正式权益，并按实销日期归期。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EntitlementGenerationIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 实销确认后预享受转正式权益并按实销日期归期() {
        Map<String, Object> policyBody = new HashMap<>();
        policyBody.put("name", "10 月阶段性补贴");
        policyBody.put("policyType", "PHASE_SUBSIDY");
        policyBody.put("benefitType", "AMOUNT");
        policyBody.put("benefitValue", 10000);
        policyBody.put("effFrom", LocalDate.now().minusDays(1).toString());
        policyBody.put("effTo", LocalDate.now().plusDays(30).toString());
        policyBody.put("referenceDateType", "RETAIL_DATE");
        policyBody.put("claimCycle", "MONTHLY");
        policyBody.put("claimCutoffDay", 5);
        policyBody.put("stackingRule", "ADDITIVE");
        policyBody.put("limitType", "AMOUNT");
        policyBody.put("totalAmount", 50000);
        PolicyApi.PolicyView policy = restTemplate.postForEntity("/api/ivs/policies", policyBody,
                PolicyApi.PolicyView.class).getBody();
        assertThat(policy).isNotNull();
        restTemplate.postForEntity("/api/ivs/policies/" + policy.id() + "/publish", Map.of(),
                PolicyApi.PolicyView.class);

        UUID orderId = UUID.randomUUID();
        UUID orderLineId = UUID.randomUUID();
        Map<String, Object> applyBody = new HashMap<>();
        applyBody.put("orderId", orderId.toString());
        applyBody.put("orderLineId", orderLineId.toString());
        applyBody.put("productId", UUID.randomUUID().toString());
        applyBody.put("orderLineAmount", 450000);
        applyBody.put("policyIds", List.of(policy.id().toString()));
        restTemplate.postForEntity("/api/ivs/pre-entitlements", applyBody,
                PolicyApi.PreEntitlementView[].class);

        // 实销日期 2026-10-15 → 归期 2026-10
        UUID retailId = UUID.randomUUID();
        UUID serialId = UUID.randomUUID();
        EntitlementApi[] created = restTemplate.postForEntity(
                "/api/ivs/entitlements/generate?orderId=" + orderId + "&retailId=" + retailId
                        + "&retailDate=2026-10-15&serialId=" + serialId,
                Map.of(), EntitlementApi[].class).getBody();
        assertThat(created).isNotNull().hasSize(1);
        assertThat(created[0].entitlementNo()).startsWith("ENT-");
        assertThat(created[0].faceAmount()).isEqualByComparingTo("10000.00");
        assertThat(created[0].periodCode()).isEqualTo("2026-10");
        assertThat(created[0].retailId()).isEqualTo(retailId);
        assertThat(created[0].status()).isEqualTo("PENDING_CLAIM");

        // 重复生成同一 VIN → 幂等返回已生成权益，不重复落账
        var again = restTemplate.postForEntity(
                "/api/ivs/entitlements/generate?orderId=" + orderId + "&retailId=" + retailId
                        + "&retailDate=2026-10-15&serialId=" + serialId, Map.of(),
                EntitlementApi[].class);
        assertThat(again.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(again.getBody()).isNotNull().hasSize(1);
        assertThat(again.getBody()[0].entitlementNo()).isEqualTo(created[0].entitlementNo());
    }
}
