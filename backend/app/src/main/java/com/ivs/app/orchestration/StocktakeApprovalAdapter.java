package com.ivs.app.orchestration;

import com.ivs.inventory.stocktake.StocktakeApprovalPort;
import com.ivs.platform.approval.ApprovalRuleService;
import com.ivs.platform.approval.ApprovalTaskService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** 平台审批规则适配为库存盘点端口。 */
@Component
public class StocktakeApprovalAdapter implements StocktakeApprovalPort {
    private final ApprovalRuleService rules;
    private final ApprovalTaskService tasks;

    public StocktakeApprovalAdapter(ApprovalRuleService rules, ApprovalTaskService tasks) {
        this.rules = rules;
        this.tasks = tasks;
    }

    @Override
    public AdjustmentDecision evaluate(BigDecimal differenceAmount) {
        ApprovalRuleService.Decision decision = rules.evaluate("STOCK_ADJUSTMENT", differenceAmount, null);
        return new AdjustmentDecision(decision.required(), decision.approvalPermission(), decision.reason());
    }

    @Override
    public void syncPending(UUID stocktakeId, String stocktakeNo, BigDecimal differenceAmount,
                            AdjustmentDecision decision) {
        if (decision == null || !decision.required()) {
            tasks.cancel("STOCKTAKE", stocktakeId);
            return;
        }
        tasks.sync(new ApprovalTaskService.CreateCommand("STOCKTAKE_POST", "STOCKTAKE", stocktakeId, stocktakeNo,
                "库存盘点过账审批：" + stocktakeNo,
                "盘点差异金额 " + differenceAmount.toPlainString() + " 元，" + decision.reason(),
                differenceAmount, decision.approvalPermission(), true));
    }

    @Override
    public void cancel(UUID stocktakeId) {
        tasks.cancel("STOCKTAKE", stocktakeId);
    }
}
