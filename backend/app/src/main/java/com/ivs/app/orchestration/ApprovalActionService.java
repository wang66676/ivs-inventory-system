package com.ivs.app.orchestration;

import com.ivs.inventory.stocktake.StocktakeApplicationService;
import com.ivs.platform.approval.ApprovalTaskService;
import com.ivs.shared.error.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 审批通过/驳回后的跨模块业务执行，放在 app 层保持业务模块边界。 */
@Service
public class ApprovalActionService {
    private final ApprovalTaskService tasks;
    private final StocktakeApplicationService stocktakes;

    public ApprovalActionService(ApprovalTaskService tasks, StocktakeApplicationService stocktakes) {
        this.tasks = tasks;
        this.stocktakes = stocktakes;
    }

    @Transactional
    public ApprovalTaskService.TaskView approve(UUID taskId) {
        ApprovalTaskService.TaskView task = tasks.getPendingForCurrentUser(taskId);
        switch (task.taskType()) {
            case "STOCKTAKE_POST" -> stocktakes.postApproved(task.businessId());
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
        } else {
            throw new ConflictException("APPROVAL_ACTION_UNSUPPORTED",
                    "该审批类型请在对应业务模块处理：" + task.taskType());
        }
        return tasks.resolveById(taskId, false, reason);
    }
}
