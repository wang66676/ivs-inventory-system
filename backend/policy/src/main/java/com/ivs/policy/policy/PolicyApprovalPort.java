package com.ivs.policy.policy;

import java.util.UUID;

/** 政策发布审批端口：实现位于 app 层。 */
public interface PolicyApprovalPort {
    PublishDecision evaluatePublish();

    void syncPending(UUID policyId, String policyNo, String policyName, PublishDecision decision);

    boolean resolvePending(UUID policyId, boolean approved, String reason);

    void cancel(UUID policyId);

    record PublishDecision(boolean required, String approvalPermission, String reason) {
        public static PublishDecision none() {
            return new PublishDecision(false, null, null);
        }
    }
}
