package com.ivs.policy.fund;

import com.ivs.policy.fund.api.RebateFundApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.security.CurrentActorProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 返利资金池：一个池、按厂家分账。
 *
 * <p>入池只接受已到账返利（红字发票 / 厂家打款 / 权益结算）；抵扣只允许同一厂家、
 * 不允许预抵扣、不需要审批、余额可分批使用；每笔抵扣可追溯到具体的入池记录。</p>
 */
@Service
public class RebateFundService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final RebateFundPoolRepository poolRepository;
    private final RebateFundSupplierBalanceRepository supplierBalanceRepository;
    private final RebateFundTxnRepository txnRepository;
    private final RebateOffsetOrderRepository offsetRepository;
    private final RebateOffsetLineRepository offsetLineRepository;
    private final CurrentActorProvider actors;

    public RebateFundService(RebateFundPoolRepository poolRepository,
                             RebateFundSupplierBalanceRepository supplierBalanceRepository,
                             RebateFundTxnRepository txnRepository,
                             RebateOffsetOrderRepository offsetRepository,
                             RebateOffsetLineRepository offsetLineRepository,
                             CurrentActorProvider actors) {
        this.poolRepository = poolRepository;
        this.supplierBalanceRepository = supplierBalanceRepository;
        this.txnRepository = txnRepository;
        this.offsetRepository = offsetRepository;
        this.offsetLineRepository = offsetLineRepository;
        this.actors = actors;
    }

    @Transactional(readOnly = true)
    public RebateFundApi.PoolView overview() {
        RebateFundPool pool = poolRepository.findByPoolCode(RebateFundPool.POOL_CODE).orElse(null);
        if (pool == null) {
            return new RebateFundApi.PoolView(RebateFundPool.POOL_CODE, "返利资金池", ZERO, ZERO, ZERO, false, List.of());
        }
        List<RebateFundApi.SupplierBalanceView> suppliers = supplierBalanceRepository
                .findByPoolIdOrderBySupplierIdAsc(pool.getId()).stream()
                .map(RebateFundApi.SupplierBalanceView::from).toList();
        return RebateFundApi.PoolView.from(pool, suppliers);
    }

    /** 登记返利入池（只接受已到账金额）。 */
    @Transactional
    public RebateFundApi.PoolView credit(RebateFundApi.CreditCommand command) {
        if (command.supplierId() == null) {
            throw new ConflictException("SUPPLIER_REQUIRED", "请指定返利所属厂家");
        }
        BigDecimal amount = money(command.amount());
        if (amount.signum() <= 0) {
            throw new ConflictException("REBATE_CREDIT_AMOUNT_INVALID", "入池金额必须大于 0");
        }
        RebateFundPool pool = ensurePool();
        RebateFundSupplierBalance balance = supplierBalanceForUpdate(pool.getId(), command.supplierId());
        balance.credit(amount);
        pool.credit(amount);
        supplierBalanceRepository.save(balance);
        poolRepository.save(pool);
        txnRepository.save(RebateFundTxn.credit(pool.getId(), command.supplierId(), amount, balance.getBalanceAmount(),
                command.sourceType() == null ? "OTHER" : command.sourceType(),
                command.sourceType(), command.sourceId(), command.sourceNo(), currentUserId(), command.remark()));
        return overview();
    }

    /** 抵扣预览：本次可抵 = min(该厂家可用余额, 本单金额)。 */
    @Transactional(readOnly = true)
    public RebateFundApi.OffsetPreview preview(UUID supplierId, BigDecimal maxAmount) {
        RebateFundPool pool = poolRepository.findByPoolCode(RebateFundPool.POOL_CODE).orElse(null);
        if (pool == null) {
            return new RebateFundApi.OffsetPreview(ZERO, ZERO, List.of());
        }
        BigDecimal available = supplierBalanceRepository.findByPoolIdAndSupplierId(pool.getId(), supplierId)
                .map(RebateFundSupplierBalance::getBalanceAmount).orElse(ZERO);
        BigDecimal cap = maxAmount == null ? available : money(maxAmount).max(ZERO);
        BigDecimal applied = available.min(cap);
        List<RebateFundApi.CreditOption> credits = txnRepository.findAvailableCredits(supplierId).stream()
                .map(RebateFundApi.CreditOption::from).toList();
        return new RebateFundApi.OffsetPreview(available, applied, credits);
    }

    /**
     * 入库单确认时抵扣车款（无需审批）。
     *
     * @param maxAmount    本单可抵扣上限（通常是入库单金额）
     * @param fundTxnIds   勾选的入池记录；为空则按 FIFO 自动消费
     */
    @Transactional
    public RebateFundApi.OffsetResult offset(UUID supplierId, UUID purchaseOrderId, UUID purchaseReceiptId,
                                             String receiptNo, BigDecimal maxAmount, List<UUID> fundTxnIds) {
        RebateFundPool pool = ensurePool();
        RebateFundSupplierBalance balance = supplierBalanceForUpdate(pool.getId(), supplierId);
        BigDecimal available = balance.getBalanceAmount();
        if (available.signum() <= 0) {
            throw new ConflictException("REBATE_POOL_EMPTY",
                    "该厂家暂无已到账返利，不能抵扣（系统不允许预抵扣）");
        }
        BigDecimal cap = maxAmount == null ? available : money(maxAmount).max(ZERO);
        BigDecimal applied = available.min(cap);
        if (applied.signum() <= 0) {
            throw new ConflictException("REBATE_OFFSET_AMOUNT_INVALID", "本次可抵扣金额为 0");
        }
        List<RebateFundTxn> credits = txnRepository.findAvailableCredits(supplierId);
        if (fundTxnIds != null && !fundTxnIds.isEmpty()) {
            credits = credits.stream().filter(txn -> fundTxnIds.contains(txn.getId())).toList();
            BigDecimal selected = credits.stream().map(RebateFundTxn::getRemainingAmount)
                    .reduce(ZERO, BigDecimal::add);
            if (selected.compareTo(applied) < 0) {
                throw new ConflictException("REBATE_CREDIT_SELECTION_INSUFFICIENT",
                        "所选返利记录可用金额不足，请刷新后重试");
            }
        }
        String offsetNo = "OFF-%06d".formatted(offsetRepository.nextOffsetSequence());
        BigDecimal balanceAfter = money(balance.getBalanceAmount().subtract(applied));
        RebateOffsetOrder order = offsetRepository.save(RebateOffsetOrder.create(offsetNo, pool.getId(),
                supplierId, purchaseOrderId, purchaseReceiptId, receiptNo, applied, applied, balanceAfter,
                currentUserId()));
        BigDecimal remaining = applied;
        for (RebateFundTxn credit : credits) {
            if (remaining.signum() <= 0) {
                break;
            }
            BigDecimal use = remaining.min(credit.getRemainingAmount());
            credit.consume(use);
            txnRepository.save(credit);
            offsetLineRepository.save(RebateOffsetLine.create(order.getId(), credit.getId(), use));
            remaining = remaining.subtract(use);
        }
        balance.debit(applied);
        pool.debit(applied);
        supplierBalanceRepository.save(balance);
        poolRepository.save(pool);
        txnRepository.save(RebateFundTxn.offset(pool.getId(), supplierId, applied, balanceAfter, offsetNo,
                purchaseOrderId, purchaseReceiptId, currentUserId(), "入库单 " + receiptNo + " 抵扣车款"));
        return new RebateFundApi.OffsetResult(order.getId(), offsetNo, applied, balanceAfter);
    }

    /** 取消抵扣：回补子余额与入池记录可用额，并留痕。 */
    @Transactional
    public RebateFundApi.OffsetView cancelOffset(UUID offsetId) {
        RebateOffsetOrder order = offsetRepository.findById(offsetId)
                .orElseThrow(() -> new NotFoundException("抵扣单", offsetId));
        if (order.getStatus() == OffsetStatus.CANCELLED) {
            return toView(order);
        }
        RebateFundPool pool = poolRepository.findById(order.getPoolId())
                .orElseThrow(() -> new NotFoundException("返利资金池", order.getPoolId()));
        RebateFundSupplierBalance balance = supplierBalanceForUpdate(pool.getId(), order.getSupplierId());
        for (RebateOffsetLine line : offsetLineRepository.findByOffsetId(offsetId)) {
            txnRepository.findById(line.getFundTxnId()).ifPresent(txn -> {
                txn.restore(line.getAmount());
                txnRepository.save(txn);
            });
        }
        balance.credit(order.getAppliedAmount());
        pool.credit(order.getAppliedAmount());
        supplierBalanceRepository.save(balance);
        poolRepository.save(pool);
        txnRepository.save(RebateFundTxn.adjustIn(pool.getId(), order.getSupplierId(), order.getAppliedAmount(),
                balance.getBalanceAmount(), order.getOffsetNo(), currentUserId(), "取消抵扣回补"));
        order.cancel();
        offsetRepository.save(order);
        return toView(order);
    }

    @Transactional(readOnly = true)
    public List<RebateFundApi.TxnView> txns(UUID supplierId) {
        List<RebateFundTxn> rows = supplierId == null ? txnRepository.findAllByOrderByOccurredAtDesc()
                : txnRepository.findBySupplierIdOrderByOccurredAtDesc(supplierId);
        return rows.stream().map(RebateFundApi.TxnView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<RebateFundApi.OffsetView> offsets(UUID supplierId) {
        List<RebateOffsetOrder> rows = supplierId == null ? offsetRepository.findAllByOrderByCreatedAtDesc()
                : offsetRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId);
        return rows.stream().map(this::toView).toList();
    }

    private RebateFundApi.OffsetView toView(RebateOffsetOrder order) {
        List<RebateFundApi.OffsetLineView> lines = new ArrayList<>();
        for (RebateOffsetLine line : offsetLineRepository.findByOffsetId(order.getId())) {
            String sourceNo = txnRepository.findById(line.getFundTxnId())
                    .map(RebateFundTxn::getSourceNo).orElse(null);
            lines.add(new RebateFundApi.OffsetLineView(line.getFundTxnId(), sourceNo, line.getAmount()));
        }
        return RebateFundApi.OffsetView.from(order, lines);
    }

    /** 回滚迁移期初入池：只允许尚未被抵扣的期初流水。 */
    @Transactional
    public void reverseOpening(UUID sourceId) {
        List<RebateFundTxn> txns = txnRepository
                .findBySourceTypeAndSourceIdOrderByOccurredAtAsc("OPENING", sourceId);
        if (txns.isEmpty()) return;
        RebateFundPool pool = ensurePool();
        Map<UUID, BigDecimal> bySupplier = new java.util.LinkedHashMap<>();
        for (RebateFundTxn txn : txns) {
            if (txn.getDirection() != FundDirection.IN || txn.getRemainingAmount().compareTo(txn.getAmount()) != 0) {
                throw new ConflictException("MIGRATION_FUND_USED", "期初返利已被抵扣，不能回滚迁移批次");
            }
            bySupplier.merge(txn.getSupplierId(), txn.getAmount(), BigDecimal::add);
        }
        for (Map.Entry<UUID, BigDecimal> entry : bySupplier.entrySet()) {
            RebateFundSupplierBalance balance = supplierBalanceForUpdate(pool.getId(), entry.getKey());
            try {
                balance.reverseCredit(entry.getValue());
                pool.reverseCredit(entry.getValue());
            } catch (IllegalStateException exception) {
                throw new ConflictException("MIGRATION_FUND_ROLLBACK_INVALID", exception.getMessage());
            }
            if (balance.getBalanceAmount().signum() == 0 && balance.getTotalInAmount().signum() == 0
                    && balance.getTotalOffsetAmount().signum() == 0) {
                supplierBalanceRepository.delete(balance);
            } else {
                supplierBalanceRepository.save(balance);
            }
        }
        supplierBalanceRepository.flush();
        poolRepository.save(pool);
        txnRepository.deleteAll(txns);
    }
    private RebateFundPool ensurePool() {
        return poolRepository.findByPoolCode(RebateFundPool.POOL_CODE).orElseGet(() -> {
            try {
                return poolRepository.save(RebateFundPool.create("返利资金池", null));
            } catch (DataIntegrityViolationException exception) {
                return poolRepository.findByPoolCode(RebateFundPool.POOL_CODE).orElseThrow();
            }
        });
    }

    private RebateFundSupplierBalance supplierBalanceForUpdate(UUID poolId, UUID supplierId) {
        return supplierBalanceRepository.findForUpdate(poolId, supplierId)
                .orElseGet(() -> supplierBalanceRepository.save(
                        RebateFundSupplierBalance.empty(poolId, supplierId)));
    }

    private UUID currentUserId() {
        return actors.current().map(actor -> actor.userId()).orElse(null);
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}





