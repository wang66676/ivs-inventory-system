package com.ivs.app.statistics;

import com.ivs.shared.error.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 采购收货与销售出库联动统计。 */
@Service
public class InventoryStatisticsService {
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final List<String> WAREHOUSE_TYPES =
            List.of("MAIN", "STORE", "SECONDARY", "CUSTOMER", "TEMPORARY", "OTHER", "UNKNOWN");

    private final JdbcTemplate jdbc;

    public InventoryStatisticsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public StatisticsView query(LocalDate from, LocalDate to, String businessScope, String warehouseType) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (start.isAfter(end)) {
            throw new BusinessException("STATISTICS_DATE_RANGE_INVALID", "开始日期不能晚于结束日期");
        }
        if (ChronoUnit.DAYS.between(start, end) > 3660) {
            throw new BusinessException("STATISTICS_DATE_RANGE_TOO_LARGE", "统计跨度不能超过 10 年");
        }
        String scope = businessScope == null || businessScope.isBlank()
                ? "ALL" : businessScope.toUpperCase(Locale.ROOT);
        if (!"ALL".equals(scope) && !"VEHICLE".equals(scope) && !"BATTERY".equals(scope)) {
            throw new BusinessException("STATISTICS_SCOPE_INVALID", "业务范围无效");
        }
        String type = warehouseType == null || warehouseType.isBlank()
                ? null : warehouseType.toUpperCase(Locale.ROOT);

        List<MovementRow> purchases = purchaseRows(scope, type);
        List<MovementRow> sales = salesRows(scope, type);

        Map<String, TrendAccumulator> trend = initializeMonthlyTrend(start, end);
        Map<String, WarehouseAccumulator> byWarehouseType = initializeWarehouses();
        Set<UUID> purchaseOrders = new HashSet<>();
        Set<UUID> salesOrders = new HashSet<>();
        BigDecimal purchaseQuantity = BigDecimal.ZERO;
        BigDecimal salesQuantity = BigDecimal.ZERO;

        YearMonth rangeStart = YearMonth.from(start);
        YearMonth rangeEnd = YearMonth.from(end);
        for (MovementRow row : purchases) {
            YearMonth month = purchaseOrderMonth(row.orderNo(), row.date());
            if (month.isBefore(rangeStart) || month.isAfter(rangeEnd)) continue;
            TrendAccumulator period = trend.get(month.format(MONTH));
            if (period == null) continue;
            period.purchase = period.purchase.add(row.quantity());
            WarehouseAccumulator purchaseWarehouse = byWarehouseType.computeIfAbsent(
                    row.warehouseType(), key -> new WarehouseAccumulator());
            purchaseWarehouse.purchase = purchaseWarehouse.purchase.add(row.quantity());
            purchaseQuantity = purchaseQuantity.add(row.quantity());
            if (row.orderId() != null) purchaseOrders.add(row.orderId());
        }
        for (MovementRow row : sales) {
            YearMonth month = salesOrderMonth(row.orderNo(), row.date());
            if (month.isBefore(rangeStart) || month.isAfter(rangeEnd)) continue;
            TrendAccumulator period = trend.get(month.format(MONTH));
            if (period == null) continue;
            period.sales = period.sales.add(row.quantity());
            WarehouseAccumulator salesWarehouse = byWarehouseType.computeIfAbsent(
                    row.warehouseType(), key -> new WarehouseAccumulator());
            salesWarehouse.sales = salesWarehouse.sales.add(row.quantity());
            salesQuantity = salesQuantity.add(row.quantity());
            if (row.orderId() != null) salesOrders.add(row.orderId());
        }

        List<TrendPoint> trendPoints = trend.entrySet().stream()
                .map(entry -> new TrendPoint(entry.getKey(), entry.getValue().purchase, entry.getValue().sales))
                .toList();
        List<WarehouseTypePoint> warehousePoints = byWarehouseType.entrySet().stream()
                .map(entry -> new WarehouseTypePoint(entry.getKey(), warehouseLabel(entry.getKey()),
                        entry.getValue().purchase, entry.getValue().sales))
                .toList();
        Summary summary = new Summary(scale(purchaseQuantity), scale(salesQuantity),
                scale(salesQuantity.subtract(purchaseQuantity)), purchaseOrders.size(), salesOrders.size());

        return new StatisticsView(start, end, scope, type, summary, trendPoints, warehousePoints,
                List.of("采购数量按实际收货数量统计，月份取采购订单编号第3-4位年份、第5-6位月份。",
                        "销售数量按实际出库数量统计，月份取销售订单编号第3-6位年份、第7-8位月份。",
                        "订单编号无法解析年月时，回退使用实际收货或出库日期。",
                        "仓库类型分别取实际收货仓库和实际出库仓库。",
                        "已退回、已取消及未过账数据不计入。"));
    }

    private List<MovementRow> purchaseRows(String scope, String warehouseType) {
        StringBuilder sql = new StringBuilder("""
                select cast(pr.received_at as date) as stat_date,
                       coalesce(w.type, 'UNKNOWN') as warehouse_type,
                       prl.quantity as quantity,
                       pr.order_id as order_id,
                       coalesce(po.external_order_no, po.order_no) as order_no
                  from ivs.purchase_receipt pr
                  join ivs.purchase_order po on po.id = pr.order_id
                  join ivs.purchase_receipt_line prl on prl.receipt_id = pr.id
                  join ivs.product p on p.id = prl.product_id
                  join ivs.product_category c on c.id = p.category_id
                  left join ivs.warehouse w on w.id = coalesce(prl.warehouse_id, pr.warehouse_id)
                 where pr.status = 'POSTED'
                   and (? = 'ALL' or
                        (case when upper(c.code) like 'BATTERY%' or c.name like '%电池%'
                              then 'BATTERY' else 'VEHICLE' end) = ?)
                """);
        List<Object> args = new ArrayList<>(List.of(scope, scope));
        if (warehouseType != null) {
            sql.append(" and coalesce(w.type, 'UNKNOWN') = ?");
            args.add(warehouseType);
        }
        return jdbc.query(sql.toString(), (rs, rowNum) -> new MovementRow(
                rs.getDate("stat_date").toLocalDate(), rs.getString("warehouse_type"),
                rs.getBigDecimal("quantity"), rs.getObject("order_id", UUID.class), rs.getString("order_no")), args.toArray());
    }

    private List<MovementRow> salesRows(String scope, String warehouseType) {
        StringBuilder sql = new StringBuilder("""
                select cast(sd.delivered_at as date) as stat_date,
                       coalesce(w.type, 'UNKNOWN') as warehouse_type,
                       sdl.quantity as quantity,
                       sd.order_id as order_id,
                       coalesce(so.external_order_no, so.order_no) as order_no
                  from ivs.sales_delivery sd
                  join ivs.sales_order so on so.id = sd.order_id
                  join ivs.sales_delivery_line sdl on sdl.delivery_id = sd.id
                  join ivs.product p on p.id = sdl.product_id
                  join ivs.product_category c on c.id = p.category_id
                  left join ivs.warehouse w on w.id = sd.warehouse_id
                 where sd.status = 'POSTED'
                   and (? = 'ALL' or
                        (case when upper(c.code) like 'BATTERY%' or c.name like '%电池%'
                              then 'BATTERY' else 'VEHICLE' end) = ?)
                """);
        List<Object> args = new ArrayList<>(List.of(scope, scope));
        if (warehouseType != null) {
            sql.append(" and coalesce(w.type, 'UNKNOWN') = ?");
            args.add(warehouseType);
        }
        return jdbc.query(sql.toString(), (rs, rowNum) -> new MovementRow(
                rs.getDate("stat_date").toLocalDate(), rs.getString("warehouse_type"),
                rs.getBigDecimal("quantity"), rs.getObject("order_id", UUID.class), rs.getString("order_no")), args.toArray());
    }

    private Map<String, TrendAccumulator> initializeMonthlyTrend(LocalDate from, LocalDate to) {
        Map<String, TrendAccumulator> result = new TreeMap<>();
        YearMonth cursor = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!cursor.isAfter(end)) {
            result.put(cursor.format(MONTH), new TrendAccumulator());
            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    private Map<String, WarehouseAccumulator> initializeWarehouses() {
        Map<String, WarehouseAccumulator> result = new LinkedHashMap<>();
        for (String type : WAREHOUSE_TYPES) result.put(type, new WarehouseAccumulator());
        return result;
    }

    private String warehouseLabel(String type) {
        return switch (type) {
            case "MAIN" -> "主仓";
            case "STORE" -> "门店仓";
            case "SECONDARY" -> "二级仓";
            case "CUSTOMER" -> "客户仓";
            case "TEMPORARY" -> "临时仓";
            case "OTHER" -> "其他";
            default -> "未设置";
        };
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(3, java.math.RoundingMode.HALF_UP);
    }

    private YearMonth purchaseOrderMonth(String orderNo, LocalDate fallbackDate) {
        if (orderNo != null && orderNo.length() >= 6) {
            String yearText = orderNo.substring(2, 4);
            String monthText = orderNo.substring(4, 6);
            if (yearText.chars().allMatch(Character::isDigit) && monthText.chars().allMatch(Character::isDigit)) {
                int month = Integer.parseInt(monthText);
                if (month >= 1 && month <= 12) return YearMonth.of(2000 + Integer.parseInt(yearText), month);
            }
        }
        return YearMonth.from(fallbackDate);
    }

    private YearMonth salesOrderMonth(String orderNo, LocalDate fallbackDate) {
        if (orderNo != null && orderNo.length() >= 8) {
            String yearText = orderNo.substring(2, 6);
            String monthText = orderNo.substring(6, 8);
            if (yearText.chars().allMatch(Character::isDigit) && monthText.chars().allMatch(Character::isDigit)) {
                int month = Integer.parseInt(monthText);
                if (month >= 1 && month <= 12) return YearMonth.of(Integer.parseInt(yearText), month);
            }
        }
        return YearMonth.from(fallbackDate);
    }

    private record MovementRow(LocalDate date, String warehouseType, BigDecimal quantity, UUID orderId, String orderNo) {
    }

    private static final class TrendAccumulator {
        private BigDecimal purchase = BigDecimal.ZERO;
        private BigDecimal sales = BigDecimal.ZERO;
    }

    private static final class WarehouseAccumulator {
        private BigDecimal purchase = BigDecimal.ZERO;
        private BigDecimal sales = BigDecimal.ZERO;
    }

    public record Summary(BigDecimal totalPurchaseQuantity, BigDecimal totalSalesQuantity, BigDecimal netQuantity,
                          int purchaseOrderCount, int salesOrderCount) {
    }

    public record TrendPoint(String period, BigDecimal purchaseQuantity, BigDecimal salesQuantity) {
    }

    public record WarehouseTypePoint(String warehouseType, String warehouseTypeLabel,
                                     BigDecimal purchaseQuantity, BigDecimal salesQuantity) {
    }

    public record StatisticsView(LocalDate from, LocalDate to, String businessScope,
                                 String warehouseType, Summary summary, List<TrendPoint> trend,
                                 List<WarehouseTypePoint> byWarehouseType, List<String> notes) {
    }
}
