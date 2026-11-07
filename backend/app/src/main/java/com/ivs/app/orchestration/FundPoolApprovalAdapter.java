package com.ivs.app.orchestration;

import com.ivs.platform.approval.ApprovalRuleService;
import com.ivs.platform.approval.ApprovalTaskService;
import com.ivs.policy.fund.FundPoolApprovalPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/** 平台审批规则适配为返利资金池调整端口。 */
@Component
public class FundPoolApprovalAdapter implements FundPoolApprovalPort {
    private final ApprovalRuleService rules;
    private final ApprovalTaskService tasks;

    public FundPoolApprovalAdapter(ApprovalRuleService rules, ApprovalTaskService tasks) {
        this.rules = rules;
        this.tasks = tasks;
    }

    @Override
    public AdjustmentDecision evaluate(BigDecimal amount) {
        ApprovalRuleService.Decision decision = rules.evaluate("FUND_POOL_ADJUSTMENT", amount, null);
        return new AdjustmentDecision(decision.required(), decision.approvalPermission(), decision.reason());
    }

    @Override
    public void syncPending(UUID requestId, String businessNo, BigDecimal amount,
                            AdjustmentDecision decision, String payloadJson) {
        tasks.sync(new ApprovalTaskService.CreateCommand("FUND_POOL_CREDIT", "REBATE_FUND", requestId,
                businessNo, "返利资金池人工调整审批",
                "人工入池金额 " + amount.toPlainString() + " 元，" + decision.reason(),
                amount, decision.approvalPermission(), true, payloadJson));
    }
}
