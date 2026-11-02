package com.ivs.app;

import com.ivs.masterdata.api.MasterdataApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 产品大项删除与商品五字段批量导入/导出。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ProductManagementIntegrationTest {
    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() { TestDatabaseCleaner.clean(jdbcTemplate); TestAuthSupport.authenticate(restTemplate); }

    @Test
    void 产品大项无商品时可删除_有商品时拒绝() {
        var empty = post("/api/ivs/product-categories", Map.of("code", "DEL-" + UUID.randomUUID().toString().substring(0, 6),
                "name", "可删除大项", "trackingMode", "QUANTITY", "statusTemplate", "SIMPLE", "unit", "件", "sortOrder", 0),
                MasterdataApi.CategoryView.class);
        var response = restTemplate.exchange("/api/ivs/product-categories/" + empty.id(), org.springframework.http.HttpMethod.DELETE,
                null, Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restTemplate.getForEntity("/api/ivs/product-categories/" + empty.id(), Map.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        var used = post("/api/ivs/product-categories", Map.of("code", "USED-" + UUID.randomUUID().toString().substring(0, 6),
                "name", "有商品大项", "trackingMode", "QUANTITY", "statusTemplate", "SIMPLE", "unit", "件", "sortOrder", 0),
                MasterdataApi.CategoryView.class);
        post("/api/ivs/products", Map.of("name", "占用商品", "categoryId", used.id().toString(), "unit", "件"),
                MasterdataApi.ProductView.class);
        var blocked = restTemplate.exchange("/api/ivs/product-categories/" + used.id(), org.springframework.http.HttpMethod.DELETE,
                null, Map.class);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(blocked.getBody().get("code")).isEqualTo("CATEGORY_IN_USE");
    }

    @Test
    void 商品五字段批量导入并导出() {
        var category = post("/api/ivs/product-categories", Map.of("code", "IMP-" + UUID.randomUUID().toString().substring(0, 6),
                "name", "导入商品", "trackingMode", "QUANTITY", "statusTemplate", "SIMPLE", "unit", "台", "sortOrder", 0),
                MasterdataApi.CategoryView.class);
        MasterdataApi.ProductBatchImportResult result = post("/api/ivs/products/batch-import", List.of(
                Map.of("categoryCode", category.code(), "series", "S1", "model", "M1", "unit", "台", "taxRate", 13),
                Map.of("categoryCode", "不存在", "series", "S2", "model", "M2", "unit", "台", "taxRate", 13)
        ), MasterdataApi.ProductBatchImportResult.class);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).name()).isEqualTo("S1 M1");
        assertThat(result.items().get(0).taxRate()).isEqualByComparingTo("0.1300");

        var export = restTemplate.getForEntity("/api/ivs/products/export", byte[].class);
        assertThat(export.getStatusCode()).isEqualTo(HttpStatus.OK);
        String csv = new String(export.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("商品,车系,产品名称,型号,单位,税率").contains("S1 M1");
    }

    private <T> T post(String url, Object body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}
