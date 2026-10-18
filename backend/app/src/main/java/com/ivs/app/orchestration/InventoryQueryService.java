package com.ivs.app.orchestration;

import com.ivs.inventory.stock.StockBalance;
import com.ivs.inventory.stock.StockBalanceRepository;
import com.ivs.inventory.stock.StockLedger;
import com.ivs.inventory.stock.StockLedgerRepository;
import com.ivs.inventory.stock.StockLock;
import com.ivs.inventory.stock.StockLockRepository;
import com.ivs.inventory.stock.StockLockStatus;
import com.ivs.inventory.stock.StockReconciliationService;
import com.ivs.inventory.stock.api.InventoryApi;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.api.MasterdataApi;
import com.ivs.masterdata.product.ProductCategoryService;
import com.ivs.masterdata.product.ProductService;
import com.ivs.masterdata.serial.ProductSerial;
import com.ivs.masterdata.serial.ProductSerialRepository;
import com.ivs.masterdata.warehouse.WarehouseService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 库存查询装配：把库存模块的数据与主数据（商品/大项/仓库/序列号）装配成可读视图。
 *
 * <p>放在 app 层是为了保持业务模块之间零直接依赖（《落地方案 v1.0》第 11.2 节）。</p>
 */
@Service
public class InventoryQueryService {

    private final StockBalanceRepository balanceRepository;
    private final StockLedgerRepository ledgerRepository;
    private final StockLockRepository lockRepository;
    private final StockReconciliationService reconciliationService;
    private final ProductService productService;
    private final ProductCategoryService categoryService;
    private final WarehouseService warehouseService;
    private final ProductSerialRepository serialRepository;
    private final JdbcTemplate jdbc;

    public InventoryQueryService(StockBalanceRepository balanceRepository,
                                 StockLedgerRepository ledgerRepository,
                                 StockLockRepository lockRepository,
                                 StockReconciliationService reconciliationService,
                                 ProductService productService,
                                 ProductCategoryService categoryService,
                                 WarehouseService warehouseService,
                                 ProductSerialRepository serialRepository,
                                 JdbcTemplate jdbc) {
        this.balanceRepository = balanceRepository;
        this.ledgerRepository = ledgerRepository;
        this.lockRepository = lockRepository;
        this.reconciliationService = reconciliationService;
        this.productService = productService;
        this.categoryService = categoryService;
        this.warehouseService = warehouseService;
        this.serialRepository = serialRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<InventoryApi.BalanceView> balances(UUID warehouseId, String keyword) {
        List<StockBalance> rows = warehouseId == null ? balanceRepository.findPositiveBalances()
                : balanceRepository.findByWarehouseIdOrderByProductIdAsc(warehouseId);
        String kw = keyword == null ? null : keyword.trim().toLowerCase();
        List<InventoryApi.BalanceView> views = new ArrayList<>();
        for (StockBalance balance : rows) {
            InventoryApi.BalanceView view = toBalanceView(balance);
            if (kw != null && !kw.isEmpty()) {
                String haystack = (view.productNo() + " " + view.productName() + " " + view.categoryCode()
                        + " " + view.warehouseName()).toLowerCase();
                if (!haystack.contains(kw)) {
                    continue;
                }
            }
            views.add(view);
        }
        return views;
    }

    @Transactional(readOnly = true)
    public List<InventoryApi.LedgerView> ledger(UUID productId, UUID warehouseId, int limit) {
        int max = Math.max(1, Math.min(limit, 500));
        List<StockLedger> rows = productId == null
                ? ledgerRepository.findTop200ByOrderByOccurredAtDesc()
                : ledgerRepository.findByProductIdOrderByOccurredAtDesc(productId);
        return rows.stream()
                .filter(row -> warehouseId == null || warehouseId.equals(row.getWarehouseId()))
                .limit(max)
                .map(this::toLedgerView)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryApi.LockView> locks(StockLockStatus status) {
        List<StockLock> rows = status == null ? lockRepository.findAll() : lockRepository.findByStatus(status);
        return rows.stream()
                .sorted(Comparator.comparing(StockLock::getCreatedAt).reversed())
                .map(this::toLockView)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryApi.SummaryView summary() {
        List<InventoryApi.BalanceView> balances = balances(null, null);
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal totalLocked = BigDecimal.ZERO;
        Map<String, int[]> categoryCount = new LinkedHashMap<>();
        Map<String, BigDecimal> categoryAmount = new LinkedHashMap<>();
        Map<String, int[]> warehouseCount = new LinkedHashMap<>();
        Map<String, BigDecimal> warehouseAmount = new LinkedHashMap<>();
        int quantityItems = 0;
        for (InventoryApi.BalanceView view : balances) {
            totalAmount = totalAmount.add(view.amount());
            totalLocked = totalLocked.add(view.lockedQty());
            if ("QUANTITY".equals(view.trackingMode())) {
                quantityItems++;
            }
            String category = view.categoryCode() == null ? "未分类" : view.categoryCode();
            categoryCount.computeIfAbsent(category, key -> new int[1])[0]++;
            categoryAmount.merge(category, view.amount(), BigDecimal::add);
            String warehouse = view.warehouseName() == null ? "未知仓库" : view.warehouseName();
            warehouseCount.computeIfAbsent(warehouse, key -> new int[1])[0]++;
            warehouseAmount.merge(warehouse, view.amount(), BigDecimal::add);
        }
        List<InventoryApi.CategorySummaryView> byCategory = categoryCount.entrySet().stream()
                .map(entry -> new InventoryApi.CategorySummaryView(entry.getKey(), entry.getValue()[0],
                        categoryAmount.get(entry.getKey())))
                .toList();
        List<InventoryApi.WarehouseSummaryView> byWarehouse = warehouseCount.entrySet().stream()
                .map(entry -> new InventoryApi.WarehouseSummaryView(entry.getKey(), entry.getValue()[0],
                        warehouseAmount.get(entry.getKey())))
                .toList();
        int serialInStock = (int) serialRepository.findByStatusOrderByCreatedAtDesc(SerialStatus.IN_STOCK).size();
        return new InventoryApi.SummaryView(serialInStock, quantityItems, totalAmount, totalLocked,
                byCategory, byWarehouse, ageBuckets(), reconciliationService.ledgerCount(),
                reconciliationService.reconcile().isEmpty());
    }

    /** 库龄分档：以每个「商品 + 仓库」最早一次入库时间为起点（序列号商品即车辆/电池的库龄）。 */
    @Transactional(readOnly = true)
    public List<InventoryApi.AgeBucketView> ageBuckets() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select b.product_id, b.warehouse_id, b.on_hand_qty, b.amount,
                       (select min(l.occurred_at) from ivs.stock_ledger l
                         where l.product_id = b.product_id and l.warehouse_id = b.warehouse_id
                           and l.direction = 'IN') as first_in
                  from ivs.stock_balance b
                 where b.on_hand_qty > 0
                """);
        int[] counts = new int[4];
        BigDecimal[] amounts = new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        Instant now = Instant.now();
        for (Map<String, Object> row : rows) {
            Object firstIn = row.get("first_in");
            long days = firstIn == null ? 0 : Duration.between(toInstant(firstIn), now).toDays();
            int index = days <= 30 ? 0 : days <= 60 ? 1 : days <= 90 ? 2 : 3;
            counts[index]++;
            amounts[index] = amounts[index].add(toMoney(row.get("amount")));
        }
        String[] labels = {"0-30 天", "31-60 天", "61-90 天", "90 天以上"};
        List<InventoryApi.AgeBucketView> buckets = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            buckets.add(new InventoryApi.AgeBucketView(labels[i], counts[i], amounts[i]));
        }
        return buckets;
    }

    private InventoryApi.BalanceView toBalanceView(StockBalance balance) {
        MasterdataApi.ProductView product = productService.get(balance.getProductId());
        String warehouseName = warehouseService.get(balance.getWarehouseId()).name();
        return new InventoryApi.BalanceView(balance.getId(), balance.getProductId(), product.productNo(),
                product.name(), product.categoryCode(), product.trackingMode().name(), balance.getWarehouseId(),
                warehouseName, balance.getBatchNo(), balance.getOnHandQty(), balance.getLockedQty(),
                balance.getAvailableQty(), balance.getUnitCost(), balance.getAmount(), balance.getUpdatedAt());
    }

    private InventoryApi.LedgerView toLedgerView(StockLedger ledger) {
        MasterdataApi.ProductView product = productService.get(ledger.getProductId());
        String warehouseName = warehouseService.get(ledger.getWarehouseId()).name();
        String serialNo = ledger.getSerialId() == null ? null
                : serialRepository.findById(ledger.getSerialId()).map(ProductSerial::getSerialNo).orElse(null);
        return new InventoryApi.LedgerView(ledger.getId(), ledger.getProductId(), product.productNo(),
                product.name(), ledger.getSerialId(), serialNo, ledger.getWarehouseId(), warehouseName,
                ledger.getMoveType(), ledger.getDirection(), ledger.getQuantity(), ledger.getUnitCost(),
                ledger.getAmount(), ledger.getBalanceAfter(), ledger.getBizType(), ledger.getBizNo(),
                ledger.getOccurredAt(), ledger.getRemark());
    }

    private InventoryApi.LockView toLockView(StockLock lock) {
        MasterdataApi.ProductView product = productService.get(lock.getProductId());
        String warehouseName = warehouseService.get(lock.getWarehouseId()).name();
        String serialNo = lock.getSerialId() == null ? null
                : serialRepository.findById(lock.getSerialId()).map(ProductSerial::getSerialNo).orElse(null);
        return new InventoryApi.LockView(lock.getId(), lock.getProductId(), product.productNo(), product.name(),
                lock.getSerialId(), serialNo, lock.getWarehouseId(), warehouseName, lock.getQuantity(),
                lock.getStatus(), lock.getBizType(), lock.getBizNo(), lock.getExpiresAt(), lock.getCreatedAt());
    }

    private Instant toInstant(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return Instant.now();
    }

    private BigDecimal toMoney(Object value) {
        return value instanceof BigDecimal decimal ? decimal : BigDecimal.ZERO;
    }
}