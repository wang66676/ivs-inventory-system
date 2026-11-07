package com.ivs.app.orchestration;

import com.ivs.inventory.stocktake.StocktakeApplicationService;
import com.ivs.platform.approval.ApprovalTaskService;
import com.ivs.policy.policy.PolicyApplicationService;
import com.ivs.policy.fund.RebateFundService;
import com.ivs.shared.error.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 审批通过/驳回后的跨模块业务执行，放在 app 层保持业务模块边界。 */
@Service
public class ApprovalActionService {
    private final ApprovalTaskService tasks;
    private final StocktakeApplicationService stocktakes;
    private final PolicyApplicationService policies;
    private final RebateFundService fundPool;

    public ApprovalActionService(ApprovalTaskService tasks, StocktakeApplicationService stocktakes,
                                 PolicyApplicationService policies, RebateFundService fundPool) {
        this.tasks = tasks;
        this.stocktakes = stocktakes;
        this.policies = policies;
        this.fundPool = fundPool;
    }

    @Transactional
    public ApprovalTaskService.TaskView approve(UUID taskId) {
        ApprovalTaskService.TaskView task = tasks.getPendingForCurrentUser(taskId);
        switch (task.taskType()) {
            case "STOCKTAKE_POST" -> stocktakes.postApproved(task.businessId());
            case "POLICY_PUBLISH" -> policies.publishApproved(task.businessId());
            case "SUBSIDY_POOL_ADJUSTMENT" -> policies.applyPoolAdjustment(task.businessId(), task.payloadJson());
            case "FUND_POOL_CREDIT" -> fundPool.applyApprovedCredit(task.payloadJson());
            default -> throw new ConflictException("APPROVAL_ACTION_UNSUPPORTED",
                    "该审批类型请在对应业务模块处理：" + task.taskType());
        }
        return tasks.resolveById(taskId, true, null);
    }

    @Transactional
    public ApprovalTaskService.TaskView reject(UUID taskId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ConflictException("APPROVAL_REASON_REQUIRED", "驳回时必须填写原因");
        }
        ApprovalTaskService.TaskView task = tasks.getPendingForCurrentUser(taskId);
        if ("STOCKTAKE_POST".equals(task.taskType())) {
            stocktakes.rejectApproval(task.businessId());
        } else if ("POLICY_PUBLISH".equals(task.taskType())) {
            // 政策保持草稿，申请人修改后可重新提交。
        } else if ("SUBSIDY_POOL_ADJUSTMENT".equals(task.taskType())) {
            // 驳回时不修改额度池。
        } else if ("FUND_POOL_CREDIT".equals(task.taskType())) {
            // 驳回时不修改资金池。
        } else {
            throw new ConflictException("APPROVAL_ACTION_UNSUPPORTED",
                    "该审批类型请在对应业务模块处理：" + task.taskType());
        }
        return tasks.resolveById(taskId, false, reason);
    }
}
