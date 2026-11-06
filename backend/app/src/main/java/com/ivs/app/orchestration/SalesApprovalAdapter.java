package com.ivs.app.orchestration;

import com.ivs.platform.approval.ApprovalRuleService;
import com.ivs.sales.order.SalesApprovalPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** 把平台审批规则适配为销售模块所需的低价审批端口。 */
@Component
public class SalesApprovalAdapter implements SalesApprovalPort {
    private final ApprovalRuleService rules;

    public SalesApprovalAdapter(ApprovalRuleService rules) {
        this.rules = rules;
    }

    @Override
    public boolean lowPriceRequiresApproval(BigDecimal salePrice, BigDecimal minPrice) {
        if (salePrice == null || minPrice == null || salePrice.compareTo(minPrice) >= 0) return false;
        return rules.evaluate("SALES_LOW_PRICE", minPrice.subtract(salePrice), minPrice).required();
    }
}
