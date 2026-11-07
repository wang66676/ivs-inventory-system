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
                                     String receiptNo, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) return null;
        return repository.findByPurchaseReceiptId(receiptId).orElseGet(() -> repository.save(
                Payable.fromReceipt("AP-%06d".formatted(repository.nextPayableSequence()), supplierId,
                        orderId, receiptId, orderNo, receiptNo, amount, LocalDate.now().plusDays(30))));
    }

    @Transactional(readOnly = true)
    public List<PayableApi.PayableView> list(UUID supplierId) {
        List<Payable> rows = supplierId == null ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findBySupplierIdOrderByDueDateAscCreatedAtAsc(supplierId);
        return rows.stream().map(row -> PayableApi.PayableView.from(row,
                masterData.requireSupplier(row.getSupplierId()).name())).toList();
    }

    @Transactional
    public PayableApi.PayableView pay(UUID id, PayableApi.PayCommand command) {
        Payable payable = repository.findById(id).orElseThrow(() -> new NotFoundException("应付单", id));
        try {
            payable.pay(command == null ? null : command.amount());
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

