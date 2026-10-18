package com.ivs.app;

import com.ivs.masterdata.CustomerType;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.SupplierType;
import com.ivs.masterdata.WarehouseType;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.TrackingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * M1 主数据验收：产品大项配置化（新增不改代码）、商品、序列号档案与状态机、
 * 批量导入逐条校验、仓库/供应商/客户/价目表与取价。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MasterdataApiIntegrationTest {

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
    void 产品大项可配置化新增且无需改代码() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE", "整车", TrackingMode.SERIAL, "VEHICLE", "台");
        MasterdataApi.CategoryView battery = createCategory("BATTERY", "动力电池", TrackingMode.SERIAL, "BATTERY", "组");
        MasterdataApi.CategoryView part = createCategory("PART", "配件", TrackingMode.QUANTITY, "SIMPLE", "件");
        // 第四个大项：只配置、不改代码
        MasterdataApi.CategoryView pile = createCategory("CHARGER", "充电桩", TrackingMode.SERIAL, "SIMPLE", "台");

        assertThat(vehicle.trackingMode()).isEqualTo(TrackingMode.SERIAL);
        assertThat(vehicle.statusTemplate()).isEqualTo("VEHICLE");
        assertThat(battery.statusTemplate()).isEqualTo("BATTERY");
        assertThat(part.trackingMode()).isEqualTo(TrackingMode.QUANTITY);
        assertThat(pile.trackingMode()).isEqualTo(TrackingMode.SERIAL);

        MasterdataApi.CategoryView[] all = restTemplate
                .getForEntity("/api/ivs/product-categories", MasterdataApi.CategoryView[].class).getBody();
        assertThat(all).isNotNull().hasSize(4);

        // 属性模板：整车加一个必填属性
        restTemplate.postForEntity("/api/ivs/product-categories/" + vehicle.id() + "/attr-defs", Map.of(
                "code", "COLOR", "name", "颜色", "dataType", "SELECT", "required", true,
                "optionsJson", "[\"红\",\"白\",\"蓝\"]", "sortOrder", 1
        ), MasterdataApi.AttrDefView.class);

        MasterdataApi.CategoryView refreshed = restTemplate
                .getForEntity("/api/ivs/product-categories/" + vehicle.id(), MasterdataApi.CategoryView.class).getBody();
        assertThat(refreshed).isNotNull();
        assertThat(refreshed.attrDefs()).hasSize(1);
        assertThat(refreshed.attrDefs().get(0).code()).isEqualTo("COLOR");
        assertThat(refreshed.attrDefs().get(0).required()).isTrue();
    }

    @Test
    void 数量管理大项不建立序列号档案() {
        MasterdataApi.CategoryView part = createCategory("PART", "配件", TrackingMode.QUANTITY, "SIMPLE", "件");
        MasterdataApi.ProductView product = createProduct("机油滤芯", part.id(), "件", null, null, "FL-100");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/ivs/product-serials", Map.of(
                "serialNo", "SN-PART-001", "productId", product.id().toString()
        ), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("SERIAL_NOT_TRACKED");
    }

    @Test
    void 序列号建档校验必填属性且状态机受控() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE", "整车", TrackingMode.SERIAL, "VEHICLE", "台");
        restTemplate.postForEntity("/api/ivs/product-categories/" + vehicle.id() + "/attr-defs", Map.of(
                "code", "COLOR", "name", "颜色", "dataType", "STRING", "required", true, "sortOrder", 1
        ), MasterdataApi.AttrDefView.class);
        MasterdataApi.ProductView product = createProduct("Q3 牵引车", vehicle.id(), "台", "某厂", "Q3", "T31");

        // 缺少必填扩展属性 → 拒绝
        ResponseEntity<Map> missingAttr = restTemplate.postForEntity("/api/ivs/product-serials", Map.of(
                "serialNo", "LZZ1CLWB0NA000001", "productId", product.id().toString()
        ), Map.class);
        assertThat(missingAttr.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(missingAttr.getBody().get("code")).isEqualTo("SERIAL_ATTR_REQUIRED");

        // 带属性建档成功
        MasterdataApi.SerialView serial = restTemplate.postForEntity("/api/ivs/product-serials", Map.of(
                "serialNo", "lzz1clwb0na000001",
                "productId", product.id().toString(),
                "attrsJson", "{\"COLOR\":\"红\"}",
                "purchasePrice", 420000
        ), MasterdataApi.SerialView.class).getBody();
        assertThat(serial).isNotNull();
        assertThat(serial.serialNo()).isEqualTo("LZZ1CLWB0NA000001");
        assertThat(serial.status()).isEqualTo(SerialStatus.PLANNED);
        assertThat(serial.allowedNextStatuses()).contains(SerialStatus.IN_TRANSIT);

        // 序列号唯一
        ResponseEntity<Map> duplicate = restTemplate.postForEntity("/api/ivs/product-serials", Map.of(
                "serialNo", "LZZ1CLWB0NA000001", "productId", product.id().toString(),
                "attrsJson", "{\"COLOR\":\"白\"}"
        ), Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody().get("code")).isEqualTo("SERIAL_NO_DUPLICATE");

        // 合法迁移：PLANNED → IN_TRANSIT → ARRIVED → IN_STOCK
        for (SerialStatus target : List.of(SerialStatus.IN_TRANSIT, SerialStatus.ARRIVED, SerialStatus.IN_STOCK)) {
            ResponseEntity<Map> moved = restTemplate.postForEntity(
                    "/api/ivs/product-serials/" + serial.id() + "/status",
                    Map.of("targetStatus", target.name()), Map.class);
            assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        // 非法迁移：IN_STOCK → DELIVERED 被拒绝
        ResponseEntity<Map> illegal = restTemplate.postForEntity(
                "/api/ivs/product-serials/" + serial.id() + "/status",
                Map.of("targetStatus", SerialStatus.DELIVERED.name()), Map.class);
        assertThat(illegal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(illegal.getBody().get("code")).isEqualTo("SERIAL_STATUS_INVALID");
    }

    @Test
    void 批量导入按行校验失败不影响其它行() {
        MasterdataApi.CategoryView battery = createCategory("BATTERY", "动力电池", TrackingMode.SERIAL, "BATTERY", "组");
        MasterdataApi.ProductView product = createProduct("282kWh 电池", battery.id(), "组", null, null, "B282");

        List<Map<String, Object>> commands = new ArrayList<>();
        commands.add(serialCommand("BAT-SN-0001", product.id()));
        commands.add(serialCommand("BAT-SN-0002", product.id()));
        commands.add(serialCommand("BAT-SN-0001", product.id()));

        MasterdataApi.BatchImportResult result = restTemplate.postForEntity(
                "/api/ivs/product-serials/batch-import", commands, MasterdataApi.BatchImportResult.class).getBody();

        assertThat(result).isNotNull();
        assertThat(result.created()).isEqualTo(2);
        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.items()).hasSize(2).allSatisfy(item ->
                assertThat(item.allowedNextStatuses()).contains(SerialStatus.IN_TRANSIT));
    }

    @Test
    void 仓库供应商客户价目表与取价() {
        MasterdataApi.CategoryView vehicle = createCategory("VEHICLE", "整车", TrackingMode.SERIAL, "VEHICLE", "台");
        MasterdataApi.ProductView product = createProduct("Q3 牵引车", vehicle.id(), "台", "某厂", "Q3", "T31");

        MasterdataApi.WarehouseView warehouse = restTemplate.postForEntity("/api/ivs/warehouses", Map.of(
                "code", "WH-MAIN", "name", "主仓", "type", WarehouseType.MAIN.name(), "address", "上海市"
        ), MasterdataApi.WarehouseView.class).getBody();
        assertThat(warehouse).isNotNull();
        assertThat(warehouse.code()).isEqualTo("WH-MAIN");

        ResponseEntity<Map> duplicateWarehouse = restTemplate.postForEntity("/api/ivs/warehouses", Map.of(
                "code", "WH-MAIN", "name", "重复主仓", "type", WarehouseType.MAIN.name()
        ), Map.class);
        assertThat(duplicateWarehouse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // 供应商编码留空 → 自动生成；整车厂标记 OEM
        MasterdataApi.SupplierView oem = restTemplate.postForEntity("/api/ivs/suppliers", Map.of(
                "name", "某整车厂", "supplierType", SupplierType.OEM.name(), "paymentTerms", "月结 60 天"
        ), MasterdataApi.SupplierView.class).getBody();
        assertThat(oem).isNotNull();
        assertThat(oem.code()).startsWith("SUP-");
        assertThat(oem.supplierType()).isEqualTo(SupplierType.OEM);

        MasterdataApi.CustomerView customer = restTemplate.postForEntity("/api/ivs/customers", Map.of(
                "name", "上海某物流有限公司", "customerType", CustomerType.COMPANY.name(), "creditLimit", 2000000
        ), MasterdataApi.CustomerView.class).getBody();
        assertThat(customer).isNotNull();
        assertThat(customer.code()).startsWith("CUS-");

        // 价目表 + 明细
        MasterdataApi.PriceListView priceList = restTemplate.postForEntity("/api/ivs/price-lists", Map.of(
                "name", "2026 年大客户价目表", "customerType", CustomerType.COMPANY.name(),
                "effectiveFrom", "2026-01-01", "effectiveTo", "2026-12-31"
        ), MasterdataApi.PriceListView.class).getBody();
        assertThat(priceList).isNotNull();
        assertThat(priceList.code()).startsWith("PL-");

        MasterdataApi.PriceListView withLine = restTemplate.postForEntity(
                "/api/ivs/price-lists/" + priceList.id() + "/lines",
                Map.of("productId", product.id().toString(), "price", 450000, "minPrice", 430000),
                MasterdataApi.PriceListView.class).getBody();
        assertThat(withLine).isNotNull();
        assertThat(withLine.lines()).hasSize(1);

        // 取价：命中价目表
        MasterdataApi.ProductPriceView hit = restTemplate.getForEntity(
                "/api/ivs/price-lists/lookup?productId=" + product.id() + "&customerType=COMPANY&date=2026-06-01",
                MasterdataApi.ProductPriceView.class).getBody();
        assertThat(hit).isNotNull();
        assertThat(hit.price()).isEqualByComparingTo("450000.00");
        assertThat(hit.minPrice()).isEqualByComparingTo("430000.00");
        assertThat(hit.source()).startsWith("PRICE_LIST:");

        // 取价：未命中 → 回落商品指导价
        MasterdataApi.ProductView other = createProduct("T31 自卸车", vehicle.id(), "台", "某厂", "T31", "自卸");
        MasterdataApi.ProductPriceView fallback = restTemplate.getForEntity(
                "/api/ivs/price-lists/lookup?productId=" + other.id(),
                MasterdataApi.ProductPriceView.class).getBody();
        assertThat(fallback).isNotNull();
        assertThat(fallback.source()).isEqualTo("PRODUCT_GUIDE");
    }

    private MasterdataApi.CategoryView createCategory(String code, String name, TrackingMode mode,
                                                      String template, String unit) {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", name);
        body.put("trackingMode", mode.name());
        body.put("statusTemplate", template);
        body.put("unit", unit);
        body.put("sortOrder", 0);
        return restTemplate.postForEntity("/api/ivs/product-categories", body,
                MasterdataApi.CategoryView.class).getBody();
    }

    private MasterdataApi.ProductView createProduct(String name, UUID categoryId, String unit,
                                                    String brand, String series, String model) {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("categoryId", categoryId.toString());
        body.put("unit", unit);
        if (brand != null) body.put("brand", brand);
        if (series != null) body.put("series", series);
        if (model != null) body.put("model", model);
        return restTemplate.postForEntity("/api/ivs/products", body,
                MasterdataApi.ProductView.class).getBody();
    }

    private Map<String, Object> serialCommand(String serialNo, UUID productId) {
        Map<String, Object> body = new HashMap<>();
        body.put("serialNo", serialNo);
        body.put("productId", productId.toString());
        return body;
    }
}