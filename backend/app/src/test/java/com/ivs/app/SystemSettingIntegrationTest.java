package com.ivs.app;

import com.ivs.platform.security.api.LoginResponse;
import com.ivs.platform.security.api.PlatformUserView;
import com.ivs.platform.setting.SystemSettingService;
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

/** 全局参数仅管理员可修改。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SystemSettingIntegrationTest {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() { TestDatabaseCleaner.clean(jdbcTemplate); TestAuthSupport.authenticate(restTemplate); }

    @Test
    void 管理员可修改全局参数_普通销售无权限() {
        SystemSettingService.SettingView[] settings = restTemplate.getForEntity(
                "/api/ivs/system-settings", SystemSettingService.SettingView[].class).getBody();
        assertThat(settings).isNotNull().isNotEmpty();
        assertThat(settings).anySatisfy(row -> assertThat(row.key()).isEqualTo("default.tax.rate"));

        var updated = restTemplate.exchange("/api/ivs/system-settings/default.tax.rate", HttpMethod.PUT,
                new HttpEntity<>(Map.of("value", "9")), SystemSettingService.SettingView.class);
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updated.getBody()).isNotNull();
        assertThat(updated.getBody().value()).isEqualTo("9");

        String username = "setting_sales_" + UUID.randomUUID().toString().substring(0, 6);
        restTemplate.postForEntity("/api/platform/users", Map.of("username", username,
                "displayName", "参数测试销售", "password", "sales12345", "dataScope", "SELF",
                "roles", List.of("SALES")), PlatformUserView.class);
        LoginResponse login = restTemplate.postForEntity("/api/platform/auth/login",
                Map.of("username", username, "password", "sales12345"), LoginResponse.class).getBody();
        assertThat(login).isNotNull();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(login.token());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        var forbidden = restTemplate.exchange("/api/ivs/system-settings/default.tax.rate", HttpMethod.PUT,
                new HttpEntity<>(Map.of("value", "7"), headers), Map.class);
        assertThat(forbidden.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        restTemplate.exchange("/api/ivs/system-settings/default.tax.rate", HttpMethod.PUT,
                new HttpEntity<>(Map.of("value", "13")), SystemSettingService.SettingView.class);
    }
}

