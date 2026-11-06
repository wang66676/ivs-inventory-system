package com.ivs.platform.approval;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 审批配置中心：仅系统管理员可查看与修改。 */
@RestController
@RequestMapping("/api/ivs/approval-rules")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class ApprovalRuleController {
    private final ApprovalRuleService service;

    public ApprovalRuleController(ApprovalRuleService service) {
        this.service = service;
    }

    @GetMapping
    public List<ApprovalRuleService.RuleView> list() {
        return service.list();
    }

    @PutMapping("/{ruleCode}")
    public ApprovalRuleService.RuleView update(@PathVariable("ruleCode") String ruleCode,
                                               @RequestBody ApprovalRuleService.UpdateCommand command) {
        return service.update(ruleCode, command);
    }
}
