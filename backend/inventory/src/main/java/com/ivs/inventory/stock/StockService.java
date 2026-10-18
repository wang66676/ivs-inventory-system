package com.ivs.inventory.stock;

import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 库存引擎：所有库存变动的唯一入口。
 *
 * <p>每个变动在同一个事务内完成「写台账 + 更新余额 + 非负校验」；
 * 台账只追加，余额可用台账重建，因此支持每日对账。</p>
 */
@Service
public class StockService {

    private final StockLedgerRepository ledgerRepository;
    private final StockBalanceRepository balanceRepository;
    private final StockLockRepository lockRepository;
    private final CurrentActorProvider actors;

    public StockService(StockLedgerRepository ledgerRepository,
                        StockBalanceRepository balanceRepository,
                        StockLockRepository lockRepository,
                        CurrentActorProvider actors) {
        this.ledgerRepository = ledgerRepository;
        this.balanceRepository = balanceRepository;
        this.lockRepository = lockRepository;
        this.actors = actors;
    }

    /**
     * 入库。
     *
     * @param weighted 数量商品传 true（移动加权平均），序列号商品传 false（个别计价）
     */
    @Transactional
    public StockLedger receive(UUID productId, UUID serialId, UUID warehouseId, String batchNo,
                               BigDecimal quantity, BigDecimal unitCost, boolean weighted,
                               StockMoveType moveType, String bizType, UUID bizId, String bizNo, String remark) {
        StockBalance balance = balanceForUpdate(productId, warehouseId, batchNo);
        balance.receive(quantity, unitCost, weighted);
        StockBalance saved = balanceRepository.save(balance);
        return ledgerRepository.save(StockLedger.record(productId, serialId, warehouseId, moveType,
                StockDirection.IN, quantity, unitCost, saved.getOnHandQty(), bizType, bizId, bizNo,
                operatorId(), remark));
    }

    /** 出库：序列号商品按该序列号最近一次入库成本结转，数量商品按移动加权单位成本结转。 */
    @Transactional
    public StockLedger issue(UUID productId, UUID serialId, UUID warehouseId, String batchNo,
                             BigDecimal quantity, StockMoveType moveType, String bizType, UUID bizId,
                             String bizNo, String remark) {
        StockBalance balance = balanceForUpdate(productId, warehouseId, batchNo);
        BigDecimal cost = serialId == null ? balance.getUnitCost()
                : ledgerRepository.findTopBySerialIdAndDirectionOrderByOccurredAtDesc(serialId, StockDirection.IN)
                        .map(StockLedger::getUnitCost).orElse(balance.getUnitCost());
        try {
            balance.issue(quantity, cost);
        } catch (IllegalStateException exception) {
            throw new ConflictException("STOCK_INSUFFICIENT", exception.getMessage());
        }
        StockBalance saved = balanceRepository.save(balance);
        return ledgerRepository.save(StockLedger.record(productId, serialId, warehouseId, moveType,
                StockDirection.OUT, quantity, cost, saved.getOnHandQty(), bizType, bizId, bizNo,
                operatorId(), remark));
    }

    /** 调拨发出：只能使用未被锁定的可用库存。 */
    @Transactional
    public StockLedger issueAvailable(UUID productId, UUID serialId, UUID warehouseId, String batchNo,
                                      BigDecimal quantity, StockMoveType moveType, String bizType, UUID bizId,
                                      String bizNo, String remark) {
        StockBalance balance = balanceForUpdate(productId, warehouseId, batchNo);
        if (quantity.compareTo(balance.getAvailableQty()) > 0) {
            throw new ConflictException("STOCK_NOT_AVAILABLE",
                    "可用库存不足，可用: " + balance.getAvailableQty().toPlainString());
        }
        BigDecimal cost = serialId == null ? balance.getUnitCost()
                : ledgerRepository.findTopBySerialIdAndDirectionOrderByOccurredAtDesc(serialId, StockDirection.IN)
                        .map(StockLedger::getUnitCost).orElse(balance.getUnitCost());
        try {
            balance.issue(quantity, cost);
        } catch (IllegalStateException exception) {
            throw new ConflictException("STOCK_INSUFFICIENT", exception.getMessage());
        }
        StockBalance saved = balanceRepository.save(balance);
        return ledgerRepository.save(StockLedger.record(productId, serialId, warehouseId, moveType,
                StockDirection.OUT, quantity, cost, saved.getOnHandQty(), bizType, bizId, bizNo,
                operatorId(), remark));
    }
    /** 调整到目标数量（盘点差异 / 手工调整），金额按目标数量与单位成本重算。 */
    @Transactional
    public StockLedger adjustTo(UUID productId, UUID warehouseId, String batchNo, BigDecimal targetQuantity,
                                BigDecimal targetUnitCost, String bizType, UUID bizId, String bizNo,
                                String remark) {
        StockBalance balance = balanceForUpdate(productId, warehouseId, batchNo);
        BigDecimal before = balance.getOnHandQty();
        balance.adjustTo(targetQuantity, targetUnitCost);
        StockBalance saved = balanceRepository.save(balance);
        BigDecimal delta = saved.getOnHandQty().subtract(before);
        return ledgerRepository.save(StockLedger.record(productId, null, warehouseId,
                StockMoveType.STOCKTAKE_ADJUST,
                delta.signum() >= 0 ? StockDirection.IN : StockDirection.OUT,
                delta.abs(), saved.getUnitCost(), saved.getOnHandQty(), bizType, bizId, bizNo,
                operatorId(), remark));
    }

    /** 锁货：数量商品按批次锁定，序列号商品锁定具体序列号。 */
    @Transactional
    public StockLock lock(UUID productId, UUID serialId, UUID warehouseId, String batchNo, BigDecimal quantity,
                          String bizType, UUID bizId, String bizNo, Duration validFor) {
        StockBalance balance = balanceForUpdate(productId, warehouseId, batchNo);
        try {
            balance.lock(quantity);
        } catch (IllegalStateException exception) {
            throw new ConflictException("STOCK_NOT_AVAILABLE", exception.getMessage());
        }
        balanceRepository.save(balance);
        Duration ttl = validFor == null ? Duration.ofDays(7) : validFor;
        return lockRepository.save(StockLock.create(productId, serialId, warehouseId,
                batchNo == null || batchNo.isBlank() ? StockBalance.NO_BATCH : batchNo, quantity,
                bizType, bizId, bizNo, Instant.now().plus(ttl)));
    }

    @Transactional
    public StockLock release(UUID lockId) {
        StockLock lock = lockRepository.findById(lockId)
                .orElseThrow(() -> new NotFoundException("库存锁定", lockId));
        if (!lock.isActive()) {
            return lock;
        }
        StockBalance balance = balanceForUpdate(lock.getProductId(), lock.getWarehouseId(), lock.getBatchNo());
        balance.unlock(lock.getQuantity());
        balanceRepository.save(balance);
        lock.release();
        return lockRepository.save(lock);
    }

    /** 订单出库时把锁定转为已消耗（数量由出库流程扣减，这里只关闭锁定记录）。 */
    @Transactional
    public void consumeLocks(UUID bizId) {
        List<StockLock> locks = lockRepository.findByBizIdOrderByCreatedAtAsc(bizId);
        for (StockLock lock : locks) {
            if (lock.isActive()) {
                StockBalance balance = balanceForUpdate(lock.getProductId(), lock.getWarehouseId(), lock.getBatchNo());
                balance.unlock(lock.getQuantity());
                balanceRepository.save(balance);
                lock.consume();
                lockRepository.save(lock);
            }
        }
    }

    /** 超时锁定自动释放。 */
    @Transactional
    public int releaseExpired() {
        List<StockLock> expired = lockRepository.findByStatusAndExpiresAtBefore(StockLockStatus.ACTIVE, Instant.now());
        for (StockLock lock : expired) {
            StockBalance balance = balanceForUpdate(lock.getProductId(), lock.getWarehouseId(), lock.getBatchNo());
            balance.unlock(lock.getQuantity());
            balanceRepository.save(balance);
            lock.expire();
            lockRepository.save(lock);
        }
        return expired.size();
    }

    @Transactional(readOnly = true)
    public StockBalance balance(UUID productId, UUID warehouseId, String batchNo) {
        return balanceRepository.findByProductIdAndWarehouseIdAndBatchNo(productId, warehouseId,
                        batchNo == null || batchNo.isBlank() ? StockBalance.NO_BATCH : batchNo)
                .orElseGet(() -> StockBalance.empty(productId, warehouseId, batchNo));
    }

    private StockBalance balanceForUpdate(UUID productId, UUID warehouseId, String batchNo) {
        String normalizedBatch = batchNo == null || batchNo.isBlank() ? StockBalance.NO_BATCH : batchNo.trim();
        return balanceRepository.findForUpdate(productId, warehouseId, normalizedBatch)
                .orElseGet(() -> balanceRepository.save(StockBalance.empty(productId, warehouseId, normalizedBatch)));
    }

    private UUID operatorId() {
        return actors.current().map(actor -> actor.userId()).orElse(null);
    }
}
