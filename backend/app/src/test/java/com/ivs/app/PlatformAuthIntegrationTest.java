package com.ivs.app;

import com.ivs.platform.security.PermissionCodes;
import com.ivs.platform.security.api.CurrentUserView;
import com.ivs.platform.security.api.LoginResponse;
import com.ivs.platform.security.api.PlatformUserView;
import com.ivs.platform.security.api.RolePermissionCatalogView;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M0 验收：能登录、能建用户与角色、审计可查。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class PlatformAuthIntegrationTest {

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
    void 引导管理员可登录并携带进销存权限() {
        LoginResponse login = restTemplate.postForEntity(
                "/api/platform/auth/login",
                Map.of("username", "admin", "password", "admin123"),
                LoginResponse.class).getBody();

        assertThat(login).isNotNull();
        assertThat(login.token()).isNotBlank();
        assertThat(login.roles()).contains("ADMIN");
        assertThat(login.permissions()).contains(
                PermissionCodes.IVS_READ, PermissionCodes.IVS_WRITE,
                PermissionCodes.POLICY_WRITE, PermissionCodes.FUND_OFFSET,
                PermissionCodes.USER_MANAGE);
    }

    @Test
    void 登录后可读取当前用户与权限集合() {
        var response = restTemplate.getForEntity("/api/platform/auth/me", CurrentUserView.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        CurrentUserView me = response.getBody();
        assertThat(me).isNotNull();
        assertThat(me.username()).isEqualTo("admin");
        assertThat(me.dataScope()).isEqualTo("ALL");
        assertThat(me.permissions()).contains(PermissionCodes.IVS_READ);
    }

    @Test
    void 密码错误时拒绝登录() {
        var response = restTemplate.postForEntity(
                "/api/platform/auth/login",
                Map.of("username", "admin", "password", "wrong-password"),
                Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void 可创建用户并分配角色且新用户按角色获得权限() {
        String username = "sales_" + UUID.randomUUID().toString().substring(0, 8);
        var createResponse = restTemplate.postForEntity("/api/platform/users", Map.of(
                "username", username,
                "displayName", "测试销售",
                "password", "sales12345",
                "dataScope", "SELF",
                "roles", List.of("SALES")
        ), PlatformUserView.class);

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PlatformUserView created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.roles()).contains("SALES");

        var assignResponse = restTemplate.exchange(
                "/api/platform/users/" + created.id() + "/roles",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("roles", List.of("SALES", "WAREHOUSE"))),
                PlatformUserView.class);
        assertThat(assignResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(assignResponse.getBody()).isNotNull();
        assertThat(assignResponse.getBody().roles()).contains("SALES", "WAREHOUSE");

        LoginResponse login = restTemplate.postForEntity(
                "/api/platform/auth/login",
                Map.of("username", username, "password", "sales12345"),
                LoginResponse.class).getBody();
        assertThat(login).isNotNull();
        assertThat(login.permissions()).contains(PermissionCodes.POLICY_SELECT);
        assertThat(login.permissions()).doesNotContain(PermissionCodes.USER_MANAGE, PermissionCodes.ENTITLEMENT_APPLY);
    }

    @Test
    void 组织架构可新增公司且编码不区分大小写唯一() {
        String code = "RD" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        var created = restTemplate.postForEntity("/api/platform/companies",
                Map.of("code", code, "name", "容大测试公司"), Map.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody()).containsEntry("code", code);

        var duplicate = restTemplate.postForEntity("/api/platform/companies",
                Map.of("code", code.toLowerCase(), "name", "重复公司"), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).containsEntry("code", "COMPANY_CODE_DUPLICATE");
    }

    @Test
    void 角色权限目录可查询() {
        var response = restTemplate.getForEntity("/api/platform/roles", RolePermissionCatalogView.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void 审计日志可检索到登录与写操作留痕() {
        var loginAudit = restTemplate.getForEntity("/api/platform/audit-logs?keyword=AuthLoggedIn", Map[].class);
        assertThat(loginAudit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginAudit.getBody()).isNotEmpty();

        String username = "audit_" + UUID.randomUUID().toString().substring(0, 8);
        restTemplate.postForEntity("/api/platform/users", Map.of(
                "username", username,
                "displayName", "审计测试用户",
                "password", "audit12345",
                "dataScope", "SELF",
                "roles", List.of("SALES")
        ), PlatformUserView.class);

        var writeAudit = restTemplate.getForEntity("/api/platform/audit-logs?keyword=ApiWrite", Map[].class);
        assertThat(writeAudit.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(writeAudit.getBody()).isNotEmpty();
    }

    @Test
    void 允许GitHubPages固定网页地址跨域登录() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("https://wang66676.github.io");
        headers.setAccessControlRequestMethod(HttpMethod.POST);
        headers.setAccessControlRequestHeaders(List.of("authorization", "content-type"));

        var response = restTemplate.exchange("/api/platform/auth/login", HttpMethod.OPTIONS,
                new HttpEntity<>(headers), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getAccessControlAllowOrigin())
                .isEqualTo("https://wang66676.github.io");
    }
}
