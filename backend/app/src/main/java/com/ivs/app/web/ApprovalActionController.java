package com.ivs.app.web;

import com.ivs.app.orchestration.ApprovalActionService;
import com.ivs.platform.approval.ApprovalTaskService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/** 通用审批动作入口：由 app 层分发到对应业务动作。 */
@RestController
@RequestMapping("/api/ivs/approval-actions")
@PreAuthorize("isAuthenticated()")
public class ApprovalActionController {
    private final ApprovalActionService service;

    public ApprovalActionController(ApprovalActionService service) {
        this.service = service;
    }

    @PostMapping("/{taskId}/approve")
    public ApprovalTaskService.TaskView approve(@PathVariable("taskId") UUID taskId) {
        return service.approve(taskId);
    }

    @PostMapping("/{taskId}/reject")
    public ApprovalTaskService.TaskView reject(@PathVariable("taskId") UUID taskId,
                                               @RequestBody Map<String, String> body) {
        return service.reject(taskId, body == null ? null : body.get("reason"));
    }
}
