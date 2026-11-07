package com.ivs.policy.policy.api;

import com.ivs.policy.policy.PolicyApplicationService;
import com.ivs.policy.policy.PolicyStatus;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 政策与预享受项。 */
@RestController
@RequestMapping("/api/ivs")
public class PolicyController {

    private final PolicyApplicationService service;

    public PolicyController(PolicyApplicationService service) {
        this.service = service;
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<PolicyApi.PolicyView> list(@RequestParam(required = false) PolicyStatus status) {
        return service.list(status);
    }

    @GetMapping("/policies/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public PolicyApi.PolicyView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping("/policies")
    @PreAuthorize("hasAuthority('IVS_POLICY_WRITE')")
    public ResponseEntity<PolicyApi.PolicyView> create(@Valid @RequestBody PolicyApi.SavePolicyCommand command) {
        PolicyApi.PolicyView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/policies/" + view.id())).body(view);
    }

    /** 实销确认后生成正式权益（按实销日期归期）。 */
    @GetMapping("/entitlements")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<EntitlementApi> listEntitlements(@RequestParam(required = false) UUID retailId,
                                                 @RequestParam(required = false) UUID supplierId,
                                                 @RequestParam(required = false) String periodCode,
                                                 @RequestParam(required = false) String status) {
        return service.listEntitlements(retailId, supplierId, periodCode, status);
    }
    @PostMapping("/entitlements/generate")
    @PreAuthorize("hasAuthority('IVS_ENTITLEMENT_APPLY')")
    public List<EntitlementApi> generate(@RequestParam UUID orderId, @RequestParam UUID retailId,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate retailDate,
                                        @RequestParam(required = false) UUID serialId) {
        return service.generateFromRetail(orderId, retailId, retailDate, serialId);
    }

    @PostMapping("/policies/{id}/publish")
    @PreAuthorize("hasAuthority('IVS_POLICY_APPROVE')")
    public PolicyApi.PolicyView publish(@PathVariable("id") UUID id) {
        return service.publish(id);
    }

    /** 政策维护人员提交发布审批，审批通过后才正式发布。 */
    @PostMapping("/policies/{id}/submit-publish")
    @PreAuthorize("hasAuthority('IVS_POLICY_WRITE')")
    public PolicyApi.PolicyView submitPublish(@PathVariable("id") UUID id) {
        return service.submitPublish(id);
    }

    @PostMapping("/policies/{id}/cancel")
    @PreAuthorize("hasAuthority('IVS_POLICY_APPROVE')")
    public PolicyApi.PolicyView cancel(@PathVariable("id") UUID id) {
        return service.cancel(id);
    }

    /** 条件类政策：标记条件已达成。 */
    @PostMapping("/policies/{id}/condition-met")
    @PreAuthorize("hasAuthority('IVS_SUBSIDY_POOL_MANAGE')")
    public PolicyApi.PolicyView markConditionMet(@PathVariable("id") UUID id) {
        return service.markConditionMet(id);
    }

    @GetMapping("/pre-entitlements")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<PolicyApi.PreEntitlementView> listPre(@RequestParam(required = false) UUID orderId) {
        return service.listPre(orderId);
    }

    /** 订单选政策：生成预享受项并预占额度。 */
    @PostMapping("/pre-entitlements")
    @PreAuthorize("hasAuthority('IVS_POLICY_SELECT')")
    public List<PolicyApi.PreEntitlementView> apply(
            @Valid @RequestBody PolicyApi.ApplyPreEntitlementCommand command) {
        return service.apply(command);
    }

    @PostMapping("/pre-entitlements/{id}/cancel")
    @PreAuthorize("hasAuthority('IVS_PRE_ENTITLEMENT_MANAGE')")
    public PolicyApi.PreEntitlementView cancelPre(@PathVariable("id") UUID id) {
        return service.cancelPre(id);
    }
}
