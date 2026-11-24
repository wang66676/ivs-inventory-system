package com.ivs.app;

import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.platform.security.api.LoginResponse;
import com.ivs.platform.security.api.PlatformUserView;
import com.ivs.sales.order.api.SalesApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 管理员主数据权限、登记人数据修改权、删除审批与 30 天试用到期门禁。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AccessControlLicenseIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbc);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 非管理员不可维护主数据且只能修改自己登记的数据并提交删除审批() {
        MasterdataApi.CategoryView category = post("/api/ivs/product-categories", Map.of(
                "code", "ACL-" + suffix(), "name", "权限测试商品", "trackingMode", "QUANTITY",
                "statusTemplate", "SIMPLE", "unit", "件", "sortOrder", 0), MasterdataApi.CategoryView.class);
        MasterdataApi.ProductView product = post("/api/ivs/products", Map.of(
                "name", "权限测试商品", "categoryId", category.id().toString(), "unit", "件",
                "minPrice", 100, "purchasePriceRef", 80), MasterdataApi.ProductView.class);
        MasterdataApi.WarehouseView warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "ACL-WH-" + suffix(), "name", "权限测试仓", "type", "MAIN"), MasterdataApi.WarehouseView.class);
        MasterdataApi.CustomerView customer = post("/api/ivs/customers", Map.of(
                "name", "权限测试客户", "customerType", "COMPANY", "creditLimit", 1000000,
                "paymentTermDays", 30), MasterdataApi.CustomerView.class);

        String username1 = "acl1_" + suffix();
        String username2 = "acl2_" + suffix();
        PlatformUserView user1 = createSalesUser(username1);
        createSalesUser(username2);
        LoginResponse login1 = login(username1);
        LoginResponse login2 = login(username2);

        Map<String, Object> deniedMasterData = Map.of(
                "code", "ACL-DENY-" + suffix(), "name", "不应创建", "trackingMode", "QUANTITY",
                "statusTemplate", "SIMPLE", "unit", "件", "sortOrder", 0);
        ResponseEntity<Map> denied = exchange("/api/ivs/product-categories", HttpMethod.POST,
                login1.token(), deniedMasterData, Map.class);
        assertThat(denied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> settingsDenied = exchange("/api/ivs/system-settings", HttpMethod.GET,
                login1.token(), null, Map.class);
        assertThat(settingsDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<Map> usersDenied = exchange("/api/platform/users", HttpMethod.GET,
                login1.token(), null, Map.class);
        assertThat(usersDenied.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        Map<String, Object> orderBody = salesOrderBody(customer.id(), warehouse.id(), product.id());
        ResponseEntity<SalesApi.SalesOrderView> created = exchange("/api/ivs/sales-orders", HttpMethod.POST,
                login1.token(), orderBody, SalesApi.SalesOrderView.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        SalesApi.SalesOrderView order = created.getBody();
        assertThat(order).isNotNull();
        assertThat(order.ownerId()).isEqualTo(user1.id());

        ResponseEntity<Map> otherCannotEdit = exchange("/api/ivs/sales-orders/" + order.id(), HttpMethod.PUT,
                login2.token(), orderBody, Map.class);
        assertThat(otherCannotEdit.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(otherCannotEdit.getBody().get("code")).isEqualTo("FORBIDDEN_DATA_MODIFY");

        ResponseEntity<SalesApi.SalesOrderView> ownerCanEdit = exchange("/api/ivs/sales-orders/" + order.id(),
                HttpMethod.PUT, login1.token(), orderBody, SalesApi.SalesOrderView.class);
        assertThat(ownerCanEdit.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<SalesApi.SalesOrderView> cancelled = exchange("/api/ivs/sales-orders/" + order.id() + "/cancel",
                HttpMethod.POST, login1.token(), Map.of(), SalesApi.SalesOrderView.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> directDelete = exchange("/api/ivs/sales-orders/" + order.id(), HttpMethod.DELETE,
                login1.token(), null, Map.class);
        assertThat(directDelete.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<Map> request = exchange("/api/ivs/delete-requests", HttpMethod.POST, login1.token(),
                Map.of("businessType", "SALES_ORDER", "businessId", order.id().toString(),
                        "reason", "测试删除审批"), Map.class);
        assertThat(request.getStatusCode()).isEqualTo(HttpStatus.OK);
        String requestId = String.valueOf(request.getBody().get("id"));
        assertThat(request.getBody().get("status")).isEqualTo("PENDING");

        ResponseEntity<Map[]> pending = exchange("/api/ivs/delete-requests?status=PENDING", HttpMethod.GET,
                TestAuthSupport.currentToken(), null, Map[].class);
        assertThat(pending.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(pending.getBody()).anySatisfy(row -> assertThat(row.get("id")).isEqualTo(requestId));

        ResponseEntity<Map> approved = exchange("/api/ivs/delete-requests/" + requestId + "/approve",
                HttpMethod.POST, TestAuthSupport.currentToken(), Map.of("remark", "同意删除"), Map.class);
        assertThat(approved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(approved.getBody().get("status")).isEqualTo("APPROVED");

        ResponseEntity<Map> missing = exchange("/api/ivs/sales-orders/" + order.id(), HttpMethod.GET,
                TestAuthSupport.currentToken(), null, Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void 试用期到期后禁止登录并返回作者联系方式() {
        try {
            jdbc.update("update ivs.software_license set trial_ends_at = DATEADD('DAY', -1, CURRENT_TIMESTAMP),"
                    + " licensed_until = null");
            ResponseEntity<Map> login = restTemplate.postForEntity("/api/platform/auth/login",
                    Map.of("username", "admin", "password", "admin123"), Map.class);
            assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(login.getBody()).isNotNull();
            assertThat(login.getBody().get("code")).isEqualTo("LICENSE_EXPIRED");
            assertThat(String.valueOf(login.getBody().get("message"))).contains("691288299@qq.com");

            Map<?, ?> status = restTemplate.getForEntity("/api/platform/license/status", Map.class).getBody();
            assertThat(status).isNotNull();
            assertThat(status.get("state")).isEqualTo("EXPIRED");
            assertThat(status.get("contactEmail")).isEqualTo("691288299@qq.com");

            ResponseEntity<Map> invalidCode = restTemplate.postForEntity("/api/platform/license/activate",
                    Map.of("activationCode", "invalid.code"), Map.class);
            assertThat(invalidCode.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        } finally {
            jdbc.update("update ivs.software_license set trial_ends_at = DATEADD('DAY', 30, CURRENT_TIMESTAMP),"
                    + " licensed_until = null");
        }
    }

    private PlatformUserView createSalesUser(String username) {
        return post("/api/platform/users", Map.of(
                "username", username, "displayName", username, "password", "Sales12345",
                "dataScope", "SELF", "roles", List.of("SALES")), PlatformUserView.class);
    }

    private LoginResponse login(String username) {
        return restTemplate.postForEntity("/api/platform/auth/login",
                Map.of("username", username, "password", "Sales12345"), LoginResponse.class).getBody();
    }

    private Map<String, Object> salesOrderBody(UUID customerId, UUID warehouseId, UUID productId) {
        return Map.of(
                "externalOrderNo", "SO-ACL-" + suffix(),
                "customerId", customerId.toString(),
                "warehouseId", warehouseId.toString(),
                "remark", "权限测试",
                "paymentMode", "CASH",
                "downPayment", 0,
                "installmentCount", 0,
                "lines", List.of(Map.of(
                        "productId", productId.toString(),
                        "quantity", 1,
                        "unitPrice", 120
                ))
        );
    }

    private <T> T post(String path, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(path, body, type).getBody();
    }

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, String token,
                                           Object body, Class<T> type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), type);
    }

    private String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
