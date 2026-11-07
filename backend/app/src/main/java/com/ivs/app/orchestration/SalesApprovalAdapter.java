package com.ivs.app.orchestration;

import com.ivs.platform.approval.ApprovalRuleService;
import com.ivs.platform.approval.ApprovalTaskService;
import com.ivs.sales.order.SalesApprovalPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** 把平台审批规则适配为销售模块所需的低价审批端口。 */
@Component
public class SalesApprovalAdapter implements SalesApprovalPort {
    private final ApprovalRuleService rules;
    private final ApprovalTaskService tasks;

    public SalesApprovalAdapter(ApprovalRuleService rules, ApprovalTaskService tasks) {
        this.rules = rules;
        this.tasks = tasks;
    }

    @Override
    public LowPriceDecision evaluateLowPrice(BigDecimal salePrice, BigDecimal minPrice) {
        if (salePrice == null || minPrice == null || salePrice.compareTo(minPrice) >= 0) {
            return LowPriceDecision.none();
        }
        ApprovalRuleService.Decision decision =
                rules.evaluate("SALES_LOW_PRICE", minPrice.subtract(salePrice), minPrice);
        return new LowPriceDecision(decision.required(), decision.approvalPermission(), decision.reason());
    }

    @Override
    public void syncLowPriceApproval(UUID orderId, String orderNo, BigDecimal totalAmount,
                                     LowPriceDecision decision) {
        if (decision == null || !decision.required()) {
            tasks.cancel("SALES_ORDER", orderId);
            return;
        }
        String summary = "订单 " + orderNo + " 存在低价成交，" + decision.reason()
                + "；订单总额 " + totalAmount.toPlainString() + " 元";
        tasks.sync(new ApprovalTaskService.CreateCommand("SALES_PRICE", "SALES_ORDER", orderId, orderNo,
                "销售订单低价审批：" + orderNo, summary, totalAmount, decision.approvalPermission(), true));
    }

    @Override
    public boolean resolveLowPriceApproval(UUID orderId, boolean approved, String reason) {
        return tasks.resolve("SALES_ORDER", orderId, approved, reason) != null;
    }

    @Override
    public void cancelLowPriceApproval(UUID orderId) {
        tasks.cancel("SALES_ORDER", orderId);
    }
}
