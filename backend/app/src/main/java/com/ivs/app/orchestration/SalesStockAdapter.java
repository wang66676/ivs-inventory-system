package com.ivs.app.orchestration;

import com.ivs.inventory.stock.StockBalance;
import com.ivs.inventory.stock.StockLedger;
import com.ivs.inventory.stock.StockLock;
import com.ivs.inventory.stock.StockLockRepository;
import com.ivs.inventory.stock.StockMoveType;
import com.ivs.inventory.stock.StockService;
import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.serial.ProductSerialRepository;
import com.ivs.masterdata.serial.ProductSerialService;
import com.ivs.sales.order.SalesStockPort;
import com.ivs.shared.error.ConflictException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 销售库存端口实现：锁货、出库扣减与成本结转、序列号状态联动。
 *
 * <p>出库时序列号按状态机模板推进到「已交付」（锁定 → 已售 → 已交付）。</p>
 */
@Component
public class SalesStockAdapter implements SalesStockPort {

    private static final String BIZ_TYPE = "SALES_ORDER";
    private static final String DELIVERY_BIZ_TYPE = "SALES_DELIVERY";
    private static final Duration LOCK_TTL = Duration.ofDays(7);

    private final StockService stockService;
    private final StockLockRepository lockRepository;
    private final ProductSerialService serialService;
    private final ProductSerialRepository serialRepository;

    public SalesStockAdapter(StockService stockService,
                             StockLockRepository lockRepository,
                             ProductSerialService serialService,
                             ProductSerialRepository serialRepository) {
        this.stockService = stockService;
        this.lockRepository = lockRepository;
        this.serialService = serialService;
        this.serialRepository = serialRepository;
    }

    @Override
    public void allocate(UUID orderId, String orderNo, UUID warehouseId, List<LineLock> lines) {
        for (LineLock line : lines) {
            if (!line.serialIds().isEmpty()) {
                for (UUID serialId : line.serialIds()) {
                    stockService.lock(line.productId(), serialId, warehouseId, null, BigDecimal.ONE, BIZ_TYPE,
                            orderId, line.orderLineId().toString(), LOCK_TTL);
                    moveSerial(serialId, SerialStatus.LOCKED);
                }
            } else {
                stockService.lock(line.productId(), null, warehouseId, null, line.quantity(), BIZ_TYPE, orderId,
                        line.orderLineId().toString(), LOCK_TTL);
            }
        }
    }

    @Override
    public void releaseAll(UUID orderId) {
        for (StockLock lock : activeLocks(orderId)) {
            stockService.release(lock.getId());
            if (lock.getSerialId() != null) {
                moveSerial(lock.getSerialId(), SerialStatus.IN_STOCK);
            }
        }
    }

    @Override
    public void settleLocks(UUID orderId, UUID orderLineId, UUID productId, UUID warehouseId,
                            List<UUID> deliveredSerialIds, List<UUID> remainingSerialIds,
                            BigDecimal remainingQuantity) {
        for (StockLock lock : activeLocks(orderId)) {
            if (!orderLineId.toString().equals(lock.getBizNo())) {
                continue;
            }
            stockService.release(lock.getId());
            if (lock.getSerialId() != null && !deliveredSerialIds.contains(lock.getSerialId())) {
                moveSerial(lock.getSerialId(), SerialStatus.IN_STOCK);
            }
        }
        if (remainingQuantity == null || remainingQuantity.signum() <= 0) {
            return;
        }
        if (!remainingSerialIds.isEmpty()) {
            for (UUID serialId : remainingSerialIds) {
                stockService.lock(productId, serialId, warehouseId, null, BigDecimal.ONE, BIZ_TYPE, orderId,
                        orderLineId.toString(), LOCK_TTL);
                moveSerial(serialId, SerialStatus.LOCKED);
            }
        } else {
            stockService.lock(productId, null, warehouseId, null, remainingQuantity, BIZ_TYPE, orderId,
                    orderLineId.toString(), LOCK_TTL);
        }
    }

    @Override
    public BigDecimal issue(UUID orderId, String orderNo, UUID warehouseId, UUID orderLineId, UUID productId,
                            List<UUID> serialIds, BigDecimal quantity) {
        BigDecimal cost = BigDecimal.ZERO.setScale(2);
        if (!serialIds.isEmpty()) {
            for (UUID serialId : serialIds) {
                StockLedger ledger = stockService.issue(productId, serialId, warehouseId, null, BigDecimal.ONE,
                        StockMoveType.SALE_OUT, DELIVERY_BIZ_TYPE, orderId, orderNo, "按序列号出库");
                cost = cost.add(ledger.getAmount());
                moveSerial(serialId, SerialStatus.DELIVERED);
            }
            return cost;
        }
        StockLedger ledger = stockService.issue(productId, null, warehouseId, null, quantity,
                StockMoveType.SALE_OUT, DELIVERY_BIZ_TYPE, orderId, orderNo, "按数量出库");
        return cost.add(ledger.getAmount());
    }

    @Override
    public void requireDeliverable(UUID productId, UUID warehouseId, BigDecimal quantity) {
        StockBalance balance = stockService.balance(productId, warehouseId, null);
        if (balance.getOnHandQty().compareTo(quantity) < 0) {
            throw new ConflictException("STOCK_NOT_AVAILABLE", "在库数量不足，当前在库："
                    + balance.getOnHandQty().toPlainString());
        }
    }

    private List<StockLock> activeLocks(UUID orderId) {
        return lockRepository.findByBizIdOrderByCreatedAtAsc(orderId).stream()
                .filter(StockLock::isActive)
                .toList();
    }

    /** 按状态机模板把序列号推进到目标状态（已满足则跳过，不可达则拒绝）。 */
    private void moveSerial(UUID serialId, SerialStatus target) {
        var serial = serialRepository.findById(serialId).orElse(null);
        if (serial == null || serial.getStatus() == target) {
            return;
        }
        try {
            for (SerialStatus step : serialService.statusPath(serialId, target)) {
                serialService.moveStatus(serialId, step);
            }
        } catch (RuntimeException exception) {
            throw new ConflictException("SERIAL_STATUS_INVALID", exception.getMessage());
        }
    }
}