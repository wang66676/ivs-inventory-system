package com.ivs.app.orchestration;

import com.ivs.billing.refund.RefundApprovalPort;
import com.ivs.platform.approval.ApprovalRuleService;
import com.ivs.platform.approval.ApprovalTaskService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class RefundApprovalAdapter implements RefundApprovalPort {
    private final ApprovalRuleService rules;
    private final ApprovalTaskService tasks;

    public RefundApprovalAdapter(ApprovalRuleService rules, ApprovalTaskService tasks) {
        this.rules = rules;
        this.tasks = tasks;
    }

    @Override
    public Decision evaluate(BigDecimal amount) {
        ApprovalRuleService.Decision decision = rules.evaluate("REFUND_APPROVAL", amount, null);
        return new Decision(decision.required(), decision.approvalPermission(), decision.reason());
    }

    @Override
    public void syncPending(UUID refundId, String refundNo, String customerName, BigDecimal amount, Decision decision) {
        tasks.sync(new ApprovalTaskService.CreateCommand("REFUND_APPROVAL", "REFUND", refundId, refundNo,
                "客户退款审批：" + refundNo,
                "客户 " + customerName + " 申请退款 " + amount.toPlainString() + " 元，" + decision.reason(),
                amount, decision.approvalPermission(), true));
    }

    @Override
    public boolean resolvePending(UUID refundId, boolean approved, String reason) {
        return tasks.resolve("REFUND", refundId, approved, reason) != null;
    }

    @Override
    public void cancel(UUID refundId) {
        tasks.cancel("REFUND", refundId);
    }
}
