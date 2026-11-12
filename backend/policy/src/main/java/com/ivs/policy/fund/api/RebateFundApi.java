package com.ivs.policy.fund.api;

import com.ivs.policy.fund.FundDirection;
import com.ivs.policy.fund.OffsetStatus;
import com.ivs.policy.fund.RebateFundPool;
import com.ivs.policy.fund.RebateFundSupplierBalance;
import com.ivs.policy.fund.RebateFundTxn;
import com.ivs.policy.fund.RebateOffsetOrder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 返利资金池对外契约。 */
public final class RebateFundApi {

    private RebateFundApi() {
    }

    public record SupplierBalanceView(
            UUID supplierId, BigDecimal balanceAmount, BigDecimal totalInAmount, BigDecimal totalOffsetAmount
    ) {
        public static SupplierBalanceView from(RebateFundSupplierBalance balance) {
            return new SupplierBalanceView(balance.getSupplierId(), balance.getBalanceAmount(),
                    balance.getTotalInAmount(), balance.getTotalOffsetAmount());
        }
    }

    public record PoolView(
            String poolCode, String poolName, BigDecimal balanceAmount, BigDecimal totalInAmount,
            BigDecimal totalOffsetAmount, boolean allowPreOffset, List<SupplierBalanceView> suppliers
    ) {
        public static PoolView from(RebateFundPool pool, List<SupplierBalanceView> suppliers) {
            return new PoolView(pool.getPoolCode(), pool.getPoolName(), pool.getBalanceAmount(),
                    pool.getTotalInAmount(), pool.getTotalOffsetAmount(), pool.isAllowPreOffset(), suppliers);
        }
    }

    public record TxnView(
            UUID id, UUID supplierId, FundDirection direction, String txnType, BigDecimal amount,
            BigDecimal balanceAfter, BigDecimal remainingAmount, String sourceType, String sourceNo,
            String offsetNo, UUID purchaseOrderId, UUID purchaseReceiptId, Instant occurredAt, String remark
    ) {
        public static TxnView from(RebateFundTxn txn) {
            return new TxnView(txn.getId(), txn.getSupplierId(), txn.getDirection(), txn.getTxnType(),
                    txn.getAmount(), txn.getBalanceAfter(), txn.getRemainingAmount(), txn.getSourceType(),
                    txn.getSourceNo(), txn.getOffsetNo(), txn.getPurchaseOrderId(), txn.getPurchaseReceiptId(),
                    txn.getOccurredAt(), txn.getRemark());
        }
    }

    /** 登记返利入池（只接受已到账返利：红字发票 / 厂家打款 / 权益结算）。 */
    public record CreditCommand(
            UUID supplierId, BigDecimal amount, String sourceType, String sourceNo, UUID sourceId, String remark
    ) {
    }

    public record CreditOption(
            UUID txnId, String sourceType, String sourceNo, BigDecimal remainingAmount, Instant occurredAt
    ) {
        public static CreditOption from(RebateFundTxn txn) {
            return new CreditOption(txn.getId(), txn.getSourceType(), txn.getSourceNo(),
                    txn.getRemainingAmount(), txn.getOccurredAt());
        }
    }

    /** 抵扣预览：可用余额、本次可抵金额 = min(该厂家余额, 本单金额)、可勾选的入池记录。 */
    public record OffsetPreview(
            BigDecimal availableBalance, BigDecimal appliedAmount, List<CreditOption> credits
    ) {
    }

    public record OffsetResult(UUID offsetId, String offsetNo, BigDecimal appliedAmount, BigDecimal balanceAfter) {
    }

    public record OffsetLineView(UUID fundTxnId, String sourceType, String sourceNo, BigDecimal amount) {
    }

    public record OffsetView(
            UUID id, String offsetNo, UUID supplierId, UUID purchaseOrderId, UUID purchaseReceiptId,
            String receiptNo, BigDecimal appliedAmount, BigDecimal balanceAfter, OffsetStatus status,
            Instant createdAt, List<OffsetLineView> lines
    ) {
        public static OffsetView from(RebateOffsetOrder order, List<OffsetLineView> lines) {
            return new OffsetView(order.getId(), order.getOffsetNo(), order.getSupplierId(),
                    order.getPurchaseOrderId(), order.getPurchaseReceiptId(), order.getReceiptNo(),
                    order.getAppliedAmount(), order.getBalanceAfter(), order.getStatus(),
                    order.getCreatedAt(), lines);
        }
    }
}