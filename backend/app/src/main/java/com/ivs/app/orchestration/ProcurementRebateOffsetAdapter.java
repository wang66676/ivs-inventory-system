package com.ivs.app.orchestration;

import com.ivs.policy.fund.RebateFundService;
import com.ivs.procurement.purchase.RebateOffsetPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** 采购侧返利抵扣端口实现：委托 policy 模块的资金池服务。 */
@Component
public class ProcurementRebateOffsetAdapter implements RebateOffsetPort {

    private final RebateFundService fundService;

    public ProcurementRebateOffsetAdapter(RebateFundService fundService) {
        this.fundService = fundService;
    }

    @Override
    public BigDecimal offset(UUID supplierId, UUID purchaseOrderId, UUID purchaseReceiptId, String receiptNo,
                             BigDecimal maxAmount, List<UUID> fundTxnIds) {
        return fundService.offset(supplierId, purchaseOrderId, purchaseReceiptId, receiptNo, maxAmount, fundTxnIds)
                .appliedAmount();
    }
}