package com.ivs.app.orchestration;

import com.ivs.platform.approval.ApprovalRuleService;
import com.ivs.platform.approval.ApprovalTaskService;
import com.ivs.policy.policy.PolicyApprovalPort;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** 平台审批规则适配为政策发布审批端口。 */
@Component
public class PolicyApprovalAdapter implements PolicyApprovalPort {
    private final ApprovalRuleService rules;
    private final ApprovalTaskService tasks;

    public PolicyApprovalAdapter(ApprovalRuleService rules, ApprovalTaskService tasks) {
        this.rules = rules;
        this.tasks = tasks;
    }

    @Override
    public PublishDecision evaluatePublish() {
        ApprovalRuleService.Decision decision = rules.evaluate("POLICY_PUBLISH", null, null);
        return new PublishDecision(decision.required(), decision.approvalPermission(), decision.reason());
    }

    @Override
    public void syncPending(UUID policyId, String policyNo, String policyName, PublishDecision decision) {
        if (decision == null || !decision.required()) {
            tasks.cancel("POLICY", policyId);
            return;
        }
        tasks.sync(new ApprovalTaskService.CreateCommand("POLICY_PUBLISH", "POLICY", policyId, policyNo,
                "厂家政策发布审批：" + policyName,
                "政策 " + policyNo + " 发布后可供订单选择，" + decision.reason(),
                null, decision.approvalPermission(), true));
    }

    @Override
    public boolean resolvePending(UUID policyId, boolean approved, String reason) {
        return tasks.resolve("POLICY", policyId, approved, reason) != null;
    }

    @Override
    public void cancel(UUID policyId) {
        tasks.cancel("POLICY", policyId);
    }
}
