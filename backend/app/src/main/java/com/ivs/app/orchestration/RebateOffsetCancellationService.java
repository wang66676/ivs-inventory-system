package com.ivs.app.orchestration;

import com.ivs.policy.fund.OffsetStatus;
import com.ivs.policy.fund.RebateFundService;
import com.ivs.policy.fund.api.RebateFundApi;
import com.ivs.procurement.purchase.PurchaseOrderApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 返利抵扣取消编排：先恢复采购收货与应付，再回补资金池与返利来源余额。
 * 跨业务模块只由 app 层协调，采购和 policy 模块保持零直接依赖。
 */
@Service
public class RebateOffsetCancellationService {

    private final RebateFundService fundService;
    private final PurchaseOrderApplicationService purchaseService;

    public RebateOffsetCancellationService(RebateFundService fundService,
                                           PurchaseOrderApplicationService purchaseService) {
        this.fundService = fundService;
        this.purchaseService = purchaseService;
    }

    @Transactional
    public RebateFundApi.OffsetView cancel(UUID offsetId) {
        RebateFundApi.OffsetView offset = fundService.getOffset(offsetId);
        if (offset.status() == OffsetStatus.CANCELLED) {
            return offset;
        }
        if (offset.purchaseReceiptId() != null) {
            purchaseService.restoreRebateOffset(offset.purchaseReceiptId(), offset.appliedAmount());
        }
        return fundService.cancelOffsetAfterProcurementRestore(offsetId);
    }
}
