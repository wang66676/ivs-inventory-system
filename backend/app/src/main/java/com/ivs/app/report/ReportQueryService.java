package com.ivs.app.report;

import com.ivs.inventory.stock.StockBalance;
import com.ivs.inventory.stock.StockBalanceRepository;
import com.ivs.inventory.stock.StockDirection;
import com.ivs.inventory.stock.StockLedger;
import com.ivs.inventory.stock.StockLedgerRepository;
import com.ivs.inventory.stock.StockMoveType;
import com.ivs.masterdata.product.Product;
import com.ivs.masterdata.product.ProductCategory;
import com.ivs.masterdata.product.ProductCategoryRepository;
import com.ivs.masterdata.product.ProductRepository;
import com.ivs.masterdata.warehouse.Warehouse;
import com.ivs.masterdata.warehouse.WarehouseRepository;
import com.ivs.policy.cost.PolicyCostAllocation;
import com.ivs.policy.cost.PolicyCostAllocationRepository;
import com.ivs.policy.policy.PolicyEntitlement;
import com.ivs.policy.policy.PolicyEntitlementRepository;
import com.ivs.procurement.purchase.PurchaseReceipt;
import com.ivs.procurement.purchase.PurchaseReceiptRepository;
import com.ivs.procurement.returns.PurchaseReturn;
import com.ivs.procurement.returns.PurchaseReturnRepository;
import com.ivs.procurement.returns.PurchaseReturnStatus;
import com.ivs.sales.order.SalesDelivery;
import com.ivs.sales.order.SalesDeliveryLine;
import com.ivs.sales.order.SalesDeliveryLineRepository;
import com.ivs.sales.order.SalesDeliveryRepository;
import com.ivs.sales.returns.SalesReturn;
import com.ivs.sales.returns.SalesReturnLine;
import com.ivs.sales.returns.SalesReturnLineRepository;
import com.ivs.sales.returns.SalesReturnRepository;
import com.ivs.sales.returns.SalesReturnStatus;
import com.ivs.shared.error.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 跨模块经营报表：采购、销售、退货、调拨、库存资金与政策返利。 */
@Service
public class ReportQueryService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final PurchaseReceiptRepository receiptRepository;
    private final PurchaseReturnRepository purchaseReturnRepository;
    private final SalesDeliveryRepository deliveryRepository;
    private final SalesDeliveryLineRepository deliveryLineRepository;
    private final SalesReturnRepository salesReturnRepository;
    private final SalesReturnLineRepository salesReturnLineRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockLedgerRepository ledgerRepository;
    private final PolicyCostAllocationRepository policyCostRepository;
    private final PolicyEntitlementRepository entitlementRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final WarehouseRepository warehouseRepository;

    public ReportQueryService(PurchaseReceiptRepository receiptRepository,
                              PurchaseReturnRepository purchaseReturnRepository,
                              SalesDeliveryRepository deliveryRepository,
                              SalesDeliveryLineRepository deliveryLineRepository,
                              SalesReturnRepository salesReturnRepository,
                              SalesReturnLineRepository salesReturnLineRepository,
                              StockBalanceRepository balanceRepository,
                              StockLedgerRepository ledgerRepository,
                              PolicyCostAllocationRepository policyCostRepository,
                              PolicyEntitlementRepository entitlementRepository,
                              ProductRepository productRepository,
                              ProductCategoryRepository categoryRepository,
                              WarehouseRepository warehouseRepository) {
        this.receiptRepository = receiptRepository;
        this.purchaseReturnRepository = purchaseReturnRepository;
        this.deliveryRepository = deliveryRepository;
        this.deliveryLineRepository = deliveryLineRepository;
        this.salesReturnRepository = salesReturnRepository;
        this.salesReturnLineRepository = salesReturnLineRepository;
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.policyCostRepository = policyCostRepository;
        this.entitlementRepository = entitlementRepository;
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.warehouseRepository = warehouseRepository;
    }

    @Transactional(readOnly = true)
    public ReportApi.MonthlyReport monthly(LocalDate from, LocalDate to) {
        LocalDate[] range = range(from, to);
        from = range[0];
        to = range[1];
        List<PurchaseReceipt> receipts = receiptRepository.findAllByOrderByReceivedAtDesc();
        List<PurchaseReturn> purchaseReturns = purchaseReturnRepository.findAllByOrderByCreatedAtDesc();
        List<SalesDelivery> deliveries = deliveryRepository.findAllByOrderByDeliveredAtDesc();
        List<SalesReturn> salesReturns = salesReturnRepository.findAllByOrderByCreatedAtDesc();
        List<PolicyCostAllocation> policyCosts = policyCostRepository.findAllByOrderByCreatedAtDesc();
        List<StockLedger> ledgers = ledgerRepository.findAllByOrderByOccurredAtAsc();
        List<ReportApi.MonthlyRow> rows = new ArrayList<>();
        YearMonth cursor = YearMonth.from(from);
        YearMonth endMonth = YearMonth.from(to);
        while (!cursor.isAfter(endMonth)) {
            LocalDate periodStart = cursor.atDay(1);
            LocalDate periodEnd = cursor.atEndOfMonth();
            LocalDate effectiveStart = periodStart.isBefore(from) ? from : periodStart;
            LocalDate effectiveEnd = periodEnd.isAfter(to) ? to : periodEnd;

            BigDecimal purchase = sumReceipts(receipts, effectiveStart, effectiveEnd);
            BigDecimal purchaseReturn = sumPurchaseReturns(purchaseReturns, effectiveStart, effectiveEnd);
            BigDecimal sales = sumDeliveries(deliveries, effectiveStart, effectiveEnd, false);
            BigDecimal salesReturn = sumSalesReturns(salesReturns, effectiveStart, effectiveEnd, false);
            BigDecimal cost = sumDeliveries(deliveries, effectiveStart, effectiveEnd, true);
            BigDecimal returnCost = sumSalesReturns(salesReturns, effectiveStart, effectiveEnd, true);
            BigDecimal rebate = sumPolicyCosts(policyCosts, effectiveStart, effectiveEnd);
            BigDecimal transferOut = sumLedger(ledgers, effectiveStart, effectiveEnd, StockMoveType.TRANSFER_OUT);
            BigDecimal transferIn = sumLedger(ledgers, effectiveStart, effectiveEnd, StockMoveType.TRANSFER_IN);
            BigDecimal stockValue = stockValueAt(ledgers, effectiveEnd);
            BigDecimal netSales = money(sales.subtract(salesReturn));
            BigDecimal grossProfit = money(netSales.subtract(cost).add(returnCost));
            rows.add(new ReportApi.MonthlyRow(cursor.toString(), money(purchase), money(purchaseReturn),
                    money(purchase.subtract(purchaseReturn)), money(sales), money(salesReturn), netSales,
                    money(cost), money(returnCost), grossProfit, money(rebate), money(transferOut),
                    money(transferIn), stockValue));
            cursor = cursor.plusMonths(1);
        }
        return new ReportApi.MonthlyReport(from, to, rows);
    }

    @Transactional(readOnly = true)
    public ReportApi.GrossProfitReport grossProfit(LocalDate from, LocalDate to) {
        LocalDate[] range = range(from, to);
        from = range[0];
        to = range[1];
        Map<UUID, ProfitAggregate> aggregates = new LinkedHashMap<>();
        for (SalesDelivery delivery : deliveryRepository.findAllByOrderByDeliveredAtDesc()) {
            LocalDate date = delivery.getDeliveredAt().atZone(ZONE).toLocalDate();
            if (date.isBefore(from) || date.isAfter(to)) continue;
            for (SalesDeliveryLine line : deliveryLineRepository.findByDeliveryId(delivery.getId())) {
                ProfitAggregate agg = aggregates.computeIfAbsent(line.getProductId(), ignored -> new ProfitAggregate());
                agg.salesQuantity = agg.salesQuantity.add(line.getQuantity());
                agg.salesAmount = agg.salesAmount.add(line.getUnitPrice().multiply(line.getQuantity()));
                agg.costAmount = agg.costAmount.add(line.getCostAmount());
            }
        }
        for (SalesReturn value : salesReturnRepository.findAllByOrderByCreatedAtDesc()) {
            if (value.getStatus() != SalesReturnStatus.POSTED) continue;
            LocalDate date = value.getCreatedAt().atZone(ZONE).toLocalDate();
            if (date.isBefore(from) || date.isAfter(to)) continue;
            for (SalesReturnLine line : salesReturnLineRepository.findByReturnIdOrderByCreatedAtAsc(value.getId())) {
                ProfitAggregate agg = aggregates.computeIfAbsent(line.getProductId(), ignored -> new ProfitAggregate());
                agg.salesQuantity = agg.salesQuantity.subtract(line.getQuantity());
                agg.salesReturnAmount = agg.salesReturnAmount.add(line.getAmount());
                agg.returnCostAmount = agg.returnCostAmount.add(line.getCostAmount());
            }
        }
        Map<UUID, Product> products = productMap();
        Map<UUID, BigDecimal> rebateCosts = new HashMap<>();
        for (PolicyCostAllocation cost : policyCostRepository.findAllByOrderByCreatedAtDesc()) {
            LocalDate date = cost.getCreatedAt().atZone(ZONE).toLocalDate();
            if (date.isBefore(from) || date.isAfter(to)) continue;
            PolicyEntitlement entitlement = entitlementRepository.findById(cost.getEntitlementId()).orElse(null);
            if (entitlement != null) {
                rebateCosts.merge(entitlement.getProductId(), cost.getAmount(), BigDecimal::add);
            }
        }
        List<ReportApi.GrossProfitRow> rows = new ArrayList<>();
        for (Map.Entry<UUID, ProfitAggregate> entry : aggregates.entrySet()) {
            ProfitAggregate agg = entry.getValue();
            Product product = products.get(entry.getKey());
            BigDecimal netSales = money(agg.salesAmount.subtract(agg.salesReturnAmount));
            BigDecimal netCost = money(agg.costAmount.subtract(agg.returnCostAmount));
            BigDecimal gross = money(netSales.subtract(netCost));
            BigDecimal rebate = money(rebateCosts.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            BigDecimal netProfit = money(gross.subtract(rebate));
            rows.add(new ReportApi.GrossProfitRow(entry.getKey(), product == null ? null : product.getProductNo(),
                    product == null ? null : product.getName(), qty(agg.salesQuantity), money(agg.salesAmount),
                    money(agg.salesReturnAmount), netSales, money(agg.costAmount), money(agg.returnCostAmount),
                    netCost, gross, ratio(gross, netSales), rebate, netProfit, ratio(netProfit, netSales)));
        }
        rows.sort(Comparator.comparing(ReportApi.GrossProfitRow::netSalesAmount,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return new ReportApi.GrossProfitReport(from, to, rows);
    }

    @Transactional(readOnly = true)
    public ReportApi.StockFundReport stockFunds(UUID warehouseId) {
        List<StockBalance> balances = balanceRepository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(row -> warehouseId == null || warehouseId.equals(row.getWarehouseId()))
                .filter(row -> row.getOnHandQty().signum() != 0 || row.getAmount().signum() != 0)
                .toList();
        BigDecimal total = money(balances.stream().map(StockBalance::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal totalQty = qty(balances.stream().map(StockBalance::getOnHandQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        Map<UUID, Product> products = productMap();
        Map<UUID, String> categories = categoryMap();
        Map<UUID, String> warehouses = warehouseMap();
        Map<String, Instant> firstInbound = new HashMap<>();
        for (StockLedger ledger : ledgerRepository.findAllByOrderByOccurredAtAsc()) {
            if (ledger.getDirection() == StockDirection.IN) {
                firstInbound.putIfAbsent(ledger.getProductId() + "|" + ledger.getWarehouseId(), ledger.getOccurredAt());
            }
        }
        List<ReportApi.StockFundRow> rows = new ArrayList<>();
        for (StockBalance balance : balances) {
            Product product = products.get(balance.getProductId());
            String categoryName = product == null ? null : categories.get(product.getCategoryId());
            Instant oldest = firstInbound.get(balance.getProductId() + "|" + balance.getWarehouseId());
            long ageDays = oldest == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(
                    oldest.atZone(ZONE).toLocalDate(), LocalDate.now(ZONE)));
            rows.add(new ReportApi.StockFundRow(balance.getWarehouseId(),
                    warehouses.get(balance.getWarehouseId()), balance.getProductId(),
                    product == null ? null : product.getProductNo(), product == null ? null : product.getName(),
                    categoryName, qty(balance.getOnHandQty()), qty(balance.getLockedQty()),
                    money(balance.getAmount()), ratio(balance.getAmount(), total), ageDays));
        }
        rows.sort(Comparator.comparing(ReportApi.StockFundRow::amount, Comparator.reverseOrder()));
        return new ReportApi.StockFundReport(total, totalQty, rows);
    }

    private BigDecimal sumReceipts(List<PurchaseReceipt> receipts, LocalDate from, LocalDate to) {
        return money(receipts.stream().filter(row -> inRange(row.getReceivedAt(), from, to))
                .map(PurchaseReceipt::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumPurchaseReturns(List<PurchaseReturn> values, LocalDate from, LocalDate to) {
        return money(values.stream().filter(row -> row.getStatus() == PurchaseReturnStatus.POSTED)
                .filter(row -> inRange(row.getCreatedAt(), from, to))
                .map(PurchaseReturn::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumDeliveries(List<SalesDelivery> values, LocalDate from, LocalDate to, boolean cost) {
        return money(values.stream().filter(row -> inRange(row.getDeliveredAt(), from, to))
                .map(row -> cost ? row.getCostAmount() : row.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumSalesReturns(List<SalesReturn> values, LocalDate from, LocalDate to, boolean cost) {
        return money(values.stream().filter(row -> row.getStatus() == SalesReturnStatus.POSTED)
                .filter(row -> inRange(row.getCreatedAt(), from, to))
                .map(row -> cost ? row.getCostAmount() : row.getTotalAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumPolicyCosts(List<PolicyCostAllocation> values, LocalDate from, LocalDate to) {
        return money(values.stream().filter(row -> inRange(row.getCreatedAt(), from, to))
                .map(PolicyCostAllocation::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal sumLedger(List<StockLedger> values, LocalDate from, LocalDate to, StockMoveType type) {
        return money(values.stream().filter(row -> row.getMoveType() == type)
                .filter(row -> inRange(row.getOccurredAt(), from, to))
                .map(StockLedger::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal stockValueAt(List<StockLedger> ledgers, LocalDate endDate) {
        Instant end = endDate.plusDays(1).atStartOfDay(ZONE).toInstant();
        BigDecimal value = BigDecimal.ZERO;
        for (StockLedger ledger : ledgers) {
            if (ledger.getOccurredAt().isBefore(end)) {
                value = ledger.getDirection() == StockDirection.IN
                        ? value.add(ledger.getAmount()) : value.subtract(ledger.getAmount());
            }
        }
        return money(value.max(BigDecimal.ZERO));
    }

    private boolean inRange(Instant instant, LocalDate from, LocalDate to) {
        LocalDate date = instant.atZone(ZONE).toLocalDate();
        return !date.isBefore(from) && !date.isAfter(to);
    }

    private Map<UUID, Product> productMap() {
        Map<UUID, Product> map = new HashMap<>();
        for (Product product : productRepository.findAllByOrderByCreatedAtDesc()) map.put(product.getId(), product);
        return map;
    }

    private Map<UUID, String> categoryMap() {
        Map<UUID, String> map = new HashMap<>();
        for (ProductCategory category : categoryRepository.findAllByOrderBySortOrderAscCodeAsc()) {
            map.put(category.getId(), category.getName());
        }
        return map;
    }

    private Map<UUID, String> warehouseMap() {
        Map<UUID, String> map = new HashMap<>();
        for (Warehouse warehouse : warehouseRepository.findAllByOrderByCodeAsc()) map.put(warehouse.getId(), warehouse.getName());
        return map;
    }

    private LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZONE) : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) throw new ConflictException("REPORT_RANGE_INVALID", "开始日期不能晚于结束日期");
        return new LocalDate[]{start, end};
    }

    private BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) return BigDecimal.ZERO.setScale(2);
        return numerator.multiply(HUNDRED).divide(denominator, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal qty(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
    }

    private static final class ProfitAggregate {
        private BigDecimal salesQuantity = BigDecimal.ZERO;
        private BigDecimal salesAmount = BigDecimal.ZERO;
        private BigDecimal salesReturnAmount = BigDecimal.ZERO;
        private BigDecimal costAmount = BigDecimal.ZERO;
        private BigDecimal returnCostAmount = BigDecimal.ZERO;
    }
}

