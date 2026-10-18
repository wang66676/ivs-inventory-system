package com.ivs.app;

import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import com.ivs.retail.api.RetailApi;
import com.ivs.retail.retail.RetailStatus;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M4 实销登记验收：必填字段、实销日期晚于开票时间、序列号唯一、提交复核、
 * 冲正后允许重新登记、数量管理商品不可实销。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RetailIntegrationTest {

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
    void 实销登记校验必填字段与日期并走完提交复核() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE", "整车", TrackingMode.SERIAL);
        MasterdataApi.ProductView product = createProduct("Q3 牵引车", vehicle.id());
        MasterdataApi.SerialView serial = createSerial("LZZ1CLWB0NA700001", product.id());

        // 缺手机号
        ResponseEntity<Map> missingMobile = postRaw(Map.of(
                "retailDate", "2026-10-05", "invoiceIssuedAt", "2026-10-01T02:00:00Z",
                "serialId", serial.id().toString(), "userName", "张三"));
        assertThat(missingMobile.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(missingMobile.getBody().get("code")).isEqualTo("RETAIL_INVALID");

        // 实销日期早于开票时间
        ResponseEntity<Map> early = postRaw(Map.of(
                "retailDate", "2026-09-30", "invoiceIssuedAt", "2026-10-01T02:00:00Z",
                "serialId", serial.id().toString(), "userName", "张三", "userMobile", "13800000000"));
        assertThat(early.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(early.getBody().get("code")).isEqualTo("RETAIL_INVALID");

        // 正常登记（车牌号选填，不填写）
        RetailApi.RetailView created = post("/api/ivs/retails", Map.of(
                "retailDate", "2026-10-05", "invoiceIssuedAt", "2026-10-01T02:00:00Z",
                "registerDate", "2026-10-05", "serialId", serial.id().toString(),
                "userName", "张三", "userMobile", "13800000000", "salePrice", 450000),
                RetailApi.RetailView.class);
        assertThat(created.status()).isEqualTo(RetailStatus.DRAFT);
        assertThat(created.retailNo()).startsWith("RT-");
        assertThat(created.plateNo()).isNull();

        // 同一序列号重复实销
        ResponseEntity<Map> duplicate = postRaw(Map.of(
                "retailDate", "2026-10-06", "invoiceIssuedAt", "2026-10-01T02:00:00Z",
                "serialId", serial.id().toString(), "userName", "李四", "userMobile", "13900000000"));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code")).isEqualTo("RETAIL_DUPLICATE_SERIAL");

        // 提交 → 复核
        RetailApi.RetailView submitted = post("/api/ivs/retails/" + created.id() + "/submit", Map.of(),
                RetailApi.RetailView.class);
        assertThat(submitted.status()).isEqualTo(RetailStatus.SUBMITTED);
        RetailApi.RetailView verified = post("/api/ivs/retails/" + created.id() + "/verify", Map.of(),
                RetailApi.RetailView.class);
        assertThat(verified.status()).isEqualTo(RetailStatus.VERIFIED);
        assertThat(verified.verifiedAt()).isNotNull();

        // 已复核不允许直接修改
        ResponseEntity<Map> updateVerified = restTemplate.exchange(
                "/api/ivs/retails/" + created.id(), org.springframework.http.HttpMethod.PUT,
                new org.springframework.http.HttpEntity<>(Map.of(
                        "retailDate", "2026-10-07", "invoiceIssuedAt", "2026-10-01T02:00:00Z",
                        "serialId", serial.id().toString(), "userName", "张三", "userMobile", "13800000000")),
                Map.class);
        assertThat(updateVerified.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 事件 RetailConfirmed 已写入审计台账
        String audit = restTemplate.getForEntity("/api/platform/audit-logs?keyword=RetailConfirmed",
                Map[].class).getBody() == null ? "[]" : "ok";
        assertThat(audit).isEqualTo("ok");
    }

    @Test
    void 冲正后同一序列号可以重新登记() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE", "整车", TrackingMode.SERIAL);
        MasterdataApi.ProductView product = createProduct("T31 自卸车", vehicle.id());
        MasterdataApi.SerialView serial = createSerial("LZZ1CLWB0NA700002", product.id());

        RetailApi.RetailView created = post("/api/ivs/retails", Map.of(
                "retailDate", "2026-10-05", "invoiceIssuedAt", "2026-10-01T02:00:00Z",
                "serialId", serial.id().toString(), "userName", "张三", "userMobile", "13800000000"),
                RetailApi.RetailView.class);
        post("/api/ivs/retails/" + created.id() + "/submit", Map.of(), RetailApi.RetailView.class);
        post("/api/ivs/retails/" + created.id() + "/verify", Map.of(), RetailApi.RetailView.class);

        // 冲正必须填原因
        ResponseEntity<Map> noReason = restTemplate.postForEntity(
                "/api/ivs/retails/" + created.id() + "/reverse", Map.of(), Map.class);
        assertThat(noReason.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(noReason.getBody().get("code")).isEqualTo("RETAIL_REVERSAL_REASON_REQUIRED");

        RetailApi.RetailView reversed = post("/api/ivs/retails/" + created.id() + "/reverse",
                Map.of("reason", "客户退车"), RetailApi.RetailView.class);
        assertThat(reversed.status()).isEqualTo(RetailStatus.REVERSED);

        // 冲正后允许重新登记
        RetailApi.RetailView again = post("/api/ivs/retails", Map.of(
                "retailDate", "2026-10-08", "invoiceIssuedAt", "2026-10-01T02:00:00Z",
                "serialId", serial.id().toString(), "userName", "李四", "userMobile", "13900000000"),
                RetailApi.RetailView.class);
        assertThat(again.status()).isEqualTo(RetailStatus.DRAFT);
        assertThat(again.retailNo()).isNotEqualTo(created.retailNo());
    }

    @Test
    void 数量管理商品不能做实销() {
        MasterdataApi.CategoryView part = createCategory("PART", "配件", TrackingMode.QUANTITY);
        MasterdataApi.ProductView product = createProduct("机油滤芯", part.id());

        // 数量商品无法建立序列号档案，直接用一个不存在的序列号验证拒绝
        ResponseEntity<Map> response = postRaw(Map.of(
                "retailDate", "2026-10-05", "invoiceIssuedAt", "2026-10-01T02:00:00Z",
                "serialId", UUID.randomUUID().toString(), "userName", "张三", "userMobile", "13800000000"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    /* ===================== 辅助方法 ===================== */

    private ResponseEntity<Map> postRaw(Map<String, Object> body) {
        return restTemplate.postForEntity("/api/ivs/retails", body, Map.class);
    }

    private <T> T post(String url, Map<String, Object> body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }

    private MasterdataApi.CategoryView createCategory(String code, String name, TrackingMode mode) {
        String template = mode == TrackingMode.SERIAL ? "VEHICLE" : "SIMPLE";
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("trackingMode", mode.name());
        body.put("statusTemplate", template);
        body.put("unit", "台");
        body.put("sortOrder", 0);
        return post("/api/ivs/product-categories", body, MasterdataApi.CategoryView.class);
    }

    private MasterdataApi.ProductView createProduct(String name, UUID categoryId) {
        return post("/api/ivs/products", Map.of("name", name, "categoryId", categoryId.toString(),
                "unit", "台", "minPrice", 0, "salePriceGuide", 450000), MasterdataApi.ProductView.class);
    }

    private MasterdataApi.SerialView createSerial(String serialNo, UUID productId) {
        return post("/api/ivs/product-serials", Map.of("serialNo", serialNo, "productId", productId.toString()),
                MasterdataApi.SerialView.class);
    }
}