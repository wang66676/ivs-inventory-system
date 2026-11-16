package com.ivs.app.orchestration;

import com.ivs.billing.purchase.PayableCreditPort;
import com.ivs.procurement.payable.PayableService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 进项红字发票冲减应付的 app 层适配器。 */
@Component
public class PayableCreditAdapter implements PayableCreditPort {

    private final PayableService payableService;

    public PayableCreditAdapter(PayableService payableService) {
        this.payableService = payableService;
    }

    @Override
    public CreditResult apply(UUID supplierId, List<RebateLine> lines, UUID claimId, String invoiceNo) {
        PayableService.RebateCreditResult result = payableService.applyRebateCredit(supplierId,
                lines.stream().map(line -> new PayableService.RebateCreditLine(
                        line.serialNo(), line.purchaseOrderId(), line.purchaseReceiptId(), line.amount())).toList());
        return new CreditResult(result.appliedAmount(), result.unappliedAmount());
    }
}
