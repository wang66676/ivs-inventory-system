package com.ivs.app.orchestration;

import com.ivs.billing.purchase.ClaimInvoicePort;
import com.ivs.policy.claim.ClaimApplicationService;
import com.ivs.policy.claim.api.ClaimApi;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** 进项红字 → 申报单结算 的适配器（billing 通过端口调用，不直接依赖 policy）。 */
@Component
public class ClaimInvoiceAdapter implements ClaimInvoicePort {

    private final ClaimApplicationService claimService;

    public ClaimInvoiceAdapter(ClaimApplicationService claimService) {
        this.claimService = claimService;
    }

    @Override
    public ClaimInvoiceInfo validateForInvoice(UUID claimId, UUID supplierId, BigDecimal amount,
                                                java.util.List<String> serialNos) {
        ClaimApi.ClaimSettlementInfo info = claimService.validateForInvoice(claimId, supplierId, amount, serialNos);
        return new ClaimInvoiceInfo(info.claimId(), info.claimNo(), info.supplierId(), info.confirmedAmount(),
                info.allocations().stream().map(item -> new ClaimRebateAllocation(item.claimLineId(),
                        item.entitlementId(), item.serialNo(), item.purchaseOrderId(), item.purchaseReceiptId(),
                        item.confirmedAmount())).toList());
    }

    @Override
    public void settleFromInvoice(UUID claimId, UUID invoiceId, String invoiceNo) {
        claimService.settleFromInvoice(claimId, invoiceId, invoiceNo);
    }
}
