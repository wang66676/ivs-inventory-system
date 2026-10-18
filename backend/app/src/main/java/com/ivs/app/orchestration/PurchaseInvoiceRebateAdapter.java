package com.ivs.app.orchestration;

import com.ivs.billing.purchase.RebateCreditPort;
import com.ivs.policy.fund.RebateFundService;
import com.ivs.policy.fund.api.RebateFundApi;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** 进项红字发票 → 返利资金池 的适配器。 */
@Component
public class PurchaseInvoiceRebateAdapter implements RebateCreditPort {

    private final RebateFundService fundService;

    public PurchaseInvoiceRebateAdapter(RebateFundService fundService) {
        this.fundService = fundService;
    }

    @Override
    public void credit(UUID supplierId, BigDecimal amount, String sourceType, String sourceNo,
                       UUID sourceId, String remark) {
        fundService.credit(new RebateFundApi.CreditCommand(supplierId, amount, sourceType, sourceNo,
                sourceId, remark));
    }
}