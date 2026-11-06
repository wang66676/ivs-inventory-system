package com.ivs.app;

import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.platform.approval.ApprovalRuleService;
import com.ivs.platform.security.api.LoginResponse;
import com.ivs.platform.security.api.PlatformUserView;
import com.ivs.sales.order.api.SalesApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 审批配置中心：管理员维护矩阵，低价规则立即作用于销售订单。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ApprovalRuleIntegrationTest {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @AfterEach
    void restoreDefaultLowPriceRule() {
        TestAuthSupport.authenticate(restTemplate);
        restTemplate.exchange("/api/ivs/approval-rules/SALES_LOW_PRICE", HttpMethod.PUT,
                new HttpEntity<>(Map.of("enabled", true, "metricType", "DISCOUNT_PERCENT",
                        "thresholdValue", 0, "approvalPermission", "IVS_PRICE_APPROVE",
                        "description", "成交价低于最低限价且折让比例达到阈值时，必须完成价格审批。阈值 0 表示任何低价均需审批。")),
                ApprovalRuleService.RuleView.class);
    }

    @Test
    void 管理员调整低价审批阈值后销售订单立即按新规则判断() {
        ApprovalRuleService.RuleView[] rules = restTemplate.getForEntity(
                "/api/ivs/approval-rules", ApprovalRuleService.RuleView[].class).getBody();
        assertThat(rules).isNotNull().hasSize(5);
        assertThat(rules).extracting(ApprovalRuleService.RuleView::ruleCode)
                .contains("SALES_LOW_PRICE", "STOCK_ADJUSTMENT", "POLICY_PUBLISH",
                        "SUBSIDY_POOL_ADJUSTMENT", "FUND_POOL_ADJUSTMENT");

        ApprovalRuleService.RuleView updated = restTemplate.exchange(
                "/api/ivs/approval-rules/SALES_LOW_PRICE", HttpMethod.PUT,
                new HttpEntity<>(Map.of("enabled", true, "metricType", "DISCOUNT_PERCENT",
                        "thresholdValue", 10, "approvalPermission", "IVS_PRICE_APPROVE",
                        "description", "低于最低限价 10% 才需要审批")),
                ApprovalRuleService.RuleView.class).getBody();
        assertThat(updated).isNotNull();
        assertThat(updated.thresholdValue()).isEqualByComparingTo("10.0000");

        MasterdataApi.CategoryView vehicle = post("/api/ivs/product-categories", Map.of(
                "code", "APPROVAL-VEHICLE", "name", "审批测试整车", "trackingMode", TrackingMode.SERIAL.name(),
                "statusTemplate", "VEHICLE", "unit", "台", "sortOrder", 0), MasterdataApi.CategoryView.class);
        Map<String, Object> productBody = new HashMap<>();
        productBody.put("name", "审批测试车型");
        productBody.put("categoryId", vehicle.id().toString());
        productBody.put("unit", "台");
        productBody.put("minPrice", 430000);
        productBody.put("salePriceGuide", 460000);
        MasterdataApi.ProductView product = post("/api/ivs/products", productBody, MasterdataApi.ProductView.class);
        MasterdataApi.WarehouseView warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "APPROVAL-WH", "name", "审批测试仓", "type", WarehouseType.MAIN.name()),
                MasterdataApi.WarehouseView.class);
        MasterdataApi.CustomerView customer = post("/api/ivs/customers", Map.of(
                "name", "审批测试客户", "customerType", CustomerType.COMPANY.name(), "creditLimit", 1000000),
                MasterdataApi.CustomerView.class);

        SalesApi.SalesOrderView order = post("/api/ivs/sales-orders", Map.of(
                "customerId", customer.id().toString(), "warehouseId", warehouse.id().toString(),
                "lines", List.of(Map.of("productId", product.id().toString(), "quantity", 1,
                        "unitPrice", 420000))), SalesApi.SalesOrderView.class);
        assertThat(order.priceApprovalRequired()).isFalse();
        SalesApi.SalesOrderView confirmed = post("/api/ivs/sales-orders/" + order.id() + "/confirm", Map.of(),
                SalesApi.SalesOrderView.class);
        assertThat(confirmed.status().name()).isEqualTo("CONFIRMED");
    }

    @Test
    void 普通销售不能查看或修改审批配置() {
        String username = "approval_sales_" + UUID.randomUUID().toString().substring(0, 6);
        restTemplate.postForEntity("/api/platform/users", Map.of("username", username,
                "displayName", "审批配置销售", "password", "sales12345", "dataScope", "SELF",
                "roles", List.of("SALES")), PlatformUserView.class);
        LoginResponse login = restTemplate.postForEntity("/api/platform/auth/login",
                Map.of("username", username, "password", "sales12345"), LoginResponse.class).getBody();
        assertThat(login).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.token());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        var listForbidden = restTemplate.exchange("/api/ivs/approval-rules", HttpMethod.GET,
                new HttpEntity<>(headers), Map.class);
        assertThat(listForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        var updateForbidden = restTemplate.exchange("/api/ivs/approval-rules/SALES_LOW_PRICE", HttpMethod.PUT,
                new HttpEntity<>(Map.of("enabled", false, "metricType", "NONE", "thresholdValue", 0,
                        "approvalPermission", "IVS_PRICE_APPROVE"), headers), Map.class);
        assertThat(updateForbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}
