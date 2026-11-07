package com.ivs.app;

import com.ivs.billing.refund.api.RefundApi;
import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.platform.approval.ApprovalTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 独立退款流程：审批、失败、重试、成功和人工兜底。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RefundWorkflowIntegrationTest {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 退款审批后执行失败可重试并支持人工兜底() {
        MasterdataApi.CustomerView customer = post("/api/ivs/customers", Map.of(
                "name", "退款测试客户", "customerType", CustomerType.INDIVIDUAL.name(), "creditLimit", 0),
                MasterdataApi.CustomerView.class);
        RefundApi.RefundView first = createRefund(customer, 1200, "MANUAL-REFUND-1");
        assertThat(first.status().name()).isEqualTo("PENDING_APPROVAL");
        approve(first.id());
        assertThat(get(first.id()).status().name()).isEqualTo("APPROVED");

        RefundApi.RefundView failed = post("/api/ivs/refunds/" + first.id() + "/execute",
                Map.of("success", false, "failureReason", "银行通道超时"), RefundApi.RefundView.class);
        assertThat(failed.status().name()).isEqualTo("FAILED");
        assertThat(failed.retryCount()).isZero();
        RefundApi.RefundView succeeded = post("/api/ivs/refunds/" + first.id() + "/execute",
                Map.of("success", true, "remark", "重试成功"), RefundApi.RefundView.class);
        assertThat(succeeded.status().name()).isEqualTo("SUCCESS");
        assertThat(succeeded.retryCount()).isEqualTo(1);

        RefundApi.RefundView second = createRefund(customer, 800, "MANUAL-REFUND-2");
        approve(second.id());
        post("/api/ivs/refunds/" + second.id() + "/execute",
                Map.of("success", false, "failureReason", "账户信息有误"), RefundApi.RefundView.class);
        RefundApi.RefundView manual = post("/api/ivs/refunds/" + second.id() + "/manual-complete",
                Map.of("remark", "线下已退款，人工确认"), RefundApi.RefundView.class);
        assertThat(manual.status().name()).isEqualTo("SUCCESS");
        assertThat(manual.manualResolved()).isTrue();
    }

    private RefundApi.RefundView createRefund(MasterdataApi.CustomerView customer, int amount, String sourceNo) {
        return post("/api/ivs/refunds", Map.of(
                "customerId", customer.id().toString(), "amount", amount, "method", "BANK_TRANSFER",
                "sourceType", "MANUAL", "sourceNo", sourceNo, "reason", "客户退款测试"),
                RefundApi.RefundView.class);
    }

    private void approve(java.util.UUID refundId) {
        ApprovalTaskService.TaskView[] tasks = restTemplate.getForEntity(
                "/api/ivs/approval-tasks?status=PENDING", ApprovalTaskService.TaskView[].class).getBody();
        var task = java.util.Arrays.stream(tasks)
                .filter(item -> item.taskType().equals("REFUND_APPROVAL") && item.businessId().equals(refundId))
                .findFirst().orElseThrow();
        post("/api/ivs/approval-actions/" + task.id() + "/approve", Map.of(), ApprovalTaskService.TaskView.class);
    }

    private RefundApi.RefundView get(java.util.UUID id) {
        return restTemplate.getForEntity("/api/ivs/refunds/" + id, RefundApi.RefundView.class).getBody();
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}
