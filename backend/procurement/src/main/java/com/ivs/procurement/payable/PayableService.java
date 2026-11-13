package com.ivs.procurement.payable;

import com.ivs.procurement.payable.api.PayableApi;
import com.ivs.procurement.purchase.MasterDataQueryPort;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 供应商应付台账：收货生成、付款核销、采购退货冲减。 */
@Service
public class PayableService {

    private final PayableRepository repository;
    private final MasterDataQueryPort masterData;

    public PayableService(PayableRepository repository, MasterDataQueryPort masterData) {
        this.repository = repository;
        this.masterData = masterData;
    }

    @Transactional
    public Payable createFromReceipt(UUID supplierId, UUID orderId, String orderNo, UUID receiptId,
                                     String receiptNo, BigDecimal purchaseQuantity, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return null;
        return repository.findByPurchaseReceiptId(receiptId).orElseGet(() -> repository.save(
                Payable.fromReceipt("AP-%06d".formatted(repository.nextPayableSequence()), supplierId,
                        orderId, receiptId, orderNo, receiptNo, purchaseQuantity, amount,
                        LocalDate.now().plusDays(30))));
    }

    /** 取消返利抵扣时恢复采购收货对应的应付。 */
    @Transactional
    public void restoreRebateOffset(UUID supplierId, UUID orderId, String orderNo, UUID receiptId,
                                    String receiptNo, BigDecimal purchaseQuantity, BigDecimal offsetAmount) {
        BigDecimal amount = offsetAmount == null ? BigDecimal.ZERO
                : offsetAmount.setScale(2, java.math.RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new ConflictException("REBATE_OFFSET_REVERSAL_AMOUNT_INVALID", "恢复抵扣金额必须大于 0");
        }
        Payable payable = repository.findByPurchaseReceiptId(receiptId).orElse(null);
        if (payable == null) {
            createFromReceipt(supplierId, orderId, orderNo, receiptId, receiptNo, purchaseQuantity, amount);
            return;
        }
        try {
            payable.restoreRebateOffset(amount);
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new ConflictException("REBATE_OFFSET_REVERSAL_BLOCKED", exception.getMessage());
        }
        repository.save(payable);
    }

    /** 收货单退回时删除未付款、未匹配进项发票的应付。 */
    @Transactional
    public void reverseReceipt(UUID receiptId) {
        Payable payable = repository.findByPurchaseReceiptId(receiptId).orElse(null);
        if (payable == null) return;
        if (payable.getPaidAmount().signum() > 0) {
            throw new ConflictException("RECEIPT_RETURN_PAYABLE_PAID", "该收货单已发生付款，不能退回");
        }
        if (payable.getInvoiceMatchedAmount().signum() > 0) {
            throw new ConflictException("RECEIPT_RETURN_INVOICE_MATCHED", "该收货单已匹配进项发票，不能退回");
        }
        repository.delete(payable);
    }

    @Transactional(readOnly = true)
    public List<PayableApi.PayableView> list(UUID supplierId, PayableStatus status) {
        List<Payable> rows = supplierId == null ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findBySupplierIdOrderByDueDateAscCreatedAtAsc(supplierId);
        return rows.stream()
                .filter(row -> status == null || row.getStatus() == status)
                .map(row -> PayableApi.PayableView.from(row,
                        masterData.requireSupplier(row.getSupplierId()).name()))
                .toList();
    }

    @Transactional
    public PayableApi.PayableView pay(UUID id, PayableApi.PayCommand command) {
        Payable payable = repository.findById(id).orElseThrow(() -> new NotFoundException("应付单", id));
        try {
            payable.pay(command == null ? null : command.amount(), command == null ? null : command.paymentDate());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("PAYABLE_PAY_INVALID", exception.getMessage());
        }
        if (command != null && command.remark() != null && !command.remark().isBlank()) {
            payable.updateRemark(command.remark());
        }
        Payable saved = repository.save(payable);
        return PayableApi.PayableView.from(saved, masterData.requireSupplier(saved.getSupplierId()).name());
    }

    @Transactional
    public void applySupplierReturn(UUID supplierId, BigDecimal amount) {
        BigDecimal remaining = amount == null ? BigDecimal.ZERO : amount;
        for (Payable payable : repository.findOutstandingForUpdate(supplierId)) {
            if (remaining.signum() <= 0) break;
            BigDecimal reduce = payable.outstanding().min(remaining);
            payable.applyReturn(reduce);
            repository.save(payable);
            remaining = remaining.subtract(reduce);
        }
    }

    /** 记录进项发票匹配金额，由 app 层发票匹配服务调用。 */
    @Transactional
    public void matchInvoice(UUID payableId, BigDecimal amount) {
        Payable payable = repository.findById(payableId)
                .orElseThrow(() -> new NotFoundException("应付单", payableId));
        try {
            payable.matchInvoice(amount);
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("PAYABLE_INVOICE_MATCH_INVALID", exception.getMessage());
        }
        repository.save(payable);
    }

    /** 取消进项发票匹配金额。 */
    @Transactional
    public void unmatchInvoice(UUID payableId, BigDecimal amount) {
        Payable payable = repository.findById(payableId)
                .orElseThrow(() -> new NotFoundException("应付单", payableId));
        try {
            payable.unmatchInvoice(amount);
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("PAYABLE_INVOICE_MATCH_INVALID", exception.getMessage());
        }
        repository.save(payable);
    }

}

