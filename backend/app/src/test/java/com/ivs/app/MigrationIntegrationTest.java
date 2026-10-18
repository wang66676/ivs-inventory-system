package com.ivs.app;

import com.ivs.app.migration.MigrationApi;
import com.ivs.app.migration.MigrationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** M5：历史数据迁移校验、导入、对账与回滚。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MigrationIntegrationTest {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        TestAuthSupport.authenticate(restTemplate);
    }

    @Test
    void 迁移校验导入对账与回滚() {
        var warehouse = post("/api/ivs/warehouses", Map.of(
                "code", "MIGWH", "name", "迁移仓", "type", "MAIN"),
                com.ivs.masterdata.api.MasterdataApi.WarehouseView.class);
        String suffix = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        MigrationApi.Payload payload = new MigrationApi.Payload(
                "上线历史数据", "旧ERP", "迁移测试",
                List.of(new MigrationApi.CustomerRow("MC-" + suffix, "迁移客户", "INDIVIDUAL",
                        "张三", "13800000000", null, new BigDecimal("50000"), "迁移")),
                List.of(new MigrationApi.SupplierRow("MS-" + suffix, "迁移供应商", "OTHER",
                        "李四", "13900000000", null, "迁移")),
                List.of(new MigrationApi.CategoryRow("MCAT-" + suffix, "迁移配件", "QUANTITY",
                        "SIMPLE", "件", null, 99, "迁移")),
                List.of(new MigrationApi.ProductRow("MP-" + suffix, "迁移商品", "MCAT-" + suffix,
                        "件", "迁移品牌", "A", "A1", BigDecimal.ZERO, new BigDecimal("50"),
                        new BigDecimal("10"), new BigDecimal("0.13"), "迁移")),
                List.of(),
                List.of(new MigrationApi.OpeningStockRow("MP-" + suffix, "MIGWH", "-",
                        new BigDecimal("5"), new BigDecimal("10"), null, "期初库存")),
                List.of(new MigrationApi.ReceivableRow("MR-" + suffix, "MC-" + suffix,
                        new BigDecimal("1000"), new BigDecimal("200"), LocalDate.now().plusDays(30), "期初应收")),
                List.of(new MigrationApi.FundOpeningRow("MS-" + suffix, new BigDecimal("500"), "OPEN-001", "期初返利")),
                List.of(new MigrationApi.PayableRow("MAP-" + suffix, "MS-" + suffix, new BigDecimal("300"), new BigDecimal("50"), LocalDate.now().plusDays(30), "期初应付"))
        );

        MigrationApi.ValidationView validation = post("/api/ivs/migrations/validate", payload,
                MigrationApi.ValidationView.class);
        assertThat(validation.valid()).isTrue();
        assertThat(validation.errors()).isEmpty();
        assertThat(validation.totalAmount()).isEqualByComparingTo("1600.00");

        MigrationApi.BatchView batch = post("/api/ivs/migrations/import", payload, MigrationApi.BatchView.class);
        assertThat(batch.batchNo()).startsWith("MIG-");
        assertThat(batch.itemCount()).isEqualTo(8);
        assertThat(batch.status()).isEqualTo(MigrationStatus.IMPORTED);
        assertThat(batch.totalAmount()).isEqualByComparingTo("1600.00");

        MigrationApi.ReconciliationView reconciliation = restTemplate.getForEntity(
                "/api/ivs/migrations/" + batch.id() + "/reconciliation",
                MigrationApi.ReconciliationView.class).getBody();
        assertThat(reconciliation).isNotNull();
        assertThat(reconciliation.ok()).isTrue();
        assertThat(reconciliation.missingTargets()).isEmpty();

        BigDecimal stockAmount = jdbcTemplate.queryForObject(
                "select amount from ivs.stock_balance where warehouse_id=? and batch_no='-'",
                BigDecimal.class, warehouse.id());
        assertThat(stockAmount).isEqualByComparingTo("50.00");
        BigDecimal outstanding = jdbcTemplate.queryForObject(
                "select amount-received_amount from ivs.receivable where receivable_no=?",
                BigDecimal.class, "MR-" + suffix);
        assertThat(outstanding).isEqualByComparingTo("800.00");
        BigDecimal funBalance = jdbcTemplate.queryForObject(
                "select balance_amount from ivs.rebate_fund_pool", BigDecimal.class);
        assertThat(funBalance).isEqualByComparingTo("500.00");
        BigDecimal payableOutstanding = jdbcTemplate.queryForObject(
                "select amount-paid_amount from ivs.payable where payable_no=?", BigDecimal.class, "MAP-" + suffix);
        assertThat(payableOutstanding).isEqualByComparingTo("250.00");

        MigrationApi.BatchView rolledBack = restTemplate.postForEntity(
                "/api/ivs/migrations/" + batch.id() + "/rollback", Map.of(), MigrationApi.BatchView.class).getBody();
        assertThat(rolledBack).isNotNull();
        assertThat(rolledBack.status()).isEqualTo(MigrationStatus.ROLLED_BACK);
        assertThat(jdbcTemplate.queryForObject("select count(*) from ivs.stock_ledger where biz_type='MIGRATION_OPENING'",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from ivs.receivable where receivable_no=?",
                Long.class, "MR-" + suffix)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from ivs.rebate_fund_txn where source_type='OPENING'",
                Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("select count(*) from ivs.payable where payable_no=?",
                Long.class, "MAP-" + suffix)).isZero();
    }

    private <T> T post(String url, Object body, Class<T> type) {
        return restTemplate.postForEntity(url, body, type).getBody();
    }
}

