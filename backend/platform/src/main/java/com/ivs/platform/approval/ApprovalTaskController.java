package com.ivs.platform.approval;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 当前用户可审批或已处理的待办。 */
@RestController
@RequestMapping("/api/ivs/approval-tasks")
@PreAuthorize("isAuthenticated()")
public class ApprovalTaskController {
    private final ApprovalTaskService service;

    public ApprovalTaskController(ApprovalTaskService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApprovalTaskService.TaskView> list(
            @RequestParam(required = false) ApprovalTaskStatus status) {
        return service.list(status);
    }
}
