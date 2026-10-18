package com.ivs.policy.claim.api;

import com.ivs.policy.claim.ClaimApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** 权益兑现申请、申报单、厂家确认与差异处置。 */
@RestController
@RequestMapping("/api/ivs")
public class ClaimController {

    private final ClaimApplicationService service;

    public ClaimController(ClaimApplicationService service) {
        this.service = service;
    }

    @GetMapping("/oem-claims")
    @PreAuthorize("hasAuthority('IVS_CLAIM_SUBMIT') or hasAuthority('IVS_CLAIM_CONFIRM')")
    public List<ClaimApi.ClaimView> list(@RequestParam(required = false) UUID supplierId,
                                         @RequestParam(required = false) String periodCode,
                                         @RequestParam(required = false) String status) {
        return service.list(supplierId, periodCode, status);
    }

    @GetMapping("/oem-claims/{id}")
    @PreAuthorize("hasAuthority('IVS_CLAIM_SUBMIT') or hasAuthority('IVS_CLAIM_CONFIRM')")
    public ClaimApi.ClaimView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    /** 实销后申请兑现（批量）：权益 → 申报单归集。 */
    @PostMapping({"/entitlements/apply", "/pre-entitlements/apply"})
    @PreAuthorize("hasAuthority('IVS_ENTITLEMENT_APPLY')")
    public List<ClaimApi.ClaimView> apply(@Valid @RequestBody ClaimApi.ApplyClaimsCommand command) {
        return service.apply(command);
    }

    /** 单条权益发起兑现。 */
    @PostMapping("/entitlements/{id}/redeem")
    @PreAuthorize("hasAuthority('IVS_ENTITLEMENT_APPLY')")
    public List<ClaimApi.ClaimView> redeem(@PathVariable("id") UUID id) {
        return service.apply(new ClaimApi.ApplyClaimsCommand(List.of(id), false, null));
    }

    @PostMapping("/oem-claims/generate")
    @PreAuthorize("hasAuthority('IVS_CLAIM_SUBMIT')")
    public List<ClaimApi.ClaimView> generate(@RequestParam String period,
                                             @RequestParam(defaultValue = "false") boolean merge,
                                             @RequestParam(required = false) String remark) {
        return service.generate(period, merge, remark);
    }

    @PostMapping("/oem-claims/{id}/submit")
    @PreAuthorize("hasAuthority('IVS_CLAIM_SUBMIT')")
    public ClaimApi.ClaimView submit(@PathVariable("id") UUID id) {
        return service.submit(id);
    }

    @PostMapping("/oem-claims/{id}/confirm")
    @PreAuthorize("hasAuthority('IVS_CLAIM_CONFIRM')")
    public ClaimApi.ClaimView confirm(@PathVariable("id") UUID id,
                                      @Valid @RequestBody ClaimApi.ConfirmClaimCommand command) {
        return service.confirm(id, command);
    }

    @PostMapping("/oem-claims/{id}/dispute")
    @PreAuthorize("hasAuthority('IVS_CLAIM_CONFIRM')")
    public ClaimApi.ClaimView dispute(@PathVariable("id") UUID id,
                                      @Valid @RequestBody ClaimApi.DisputeClaimCommand command) {
        return service.dispute(id, command);
    }

    @GetMapping("/oem-claims/{id}/differences")
    @PreAuthorize("hasAuthority('IVS_CLAIM_CONFIRM') or hasAuthority('IVS_FINANCE_READ')")
    public List<ClaimApi.DifferenceView> differences(@PathVariable("id") UUID id) {
        return service.get(id).differences();
    }

    @PostMapping("/oem-claims/{id}/differences/{differenceId}/resolve")
    @PreAuthorize("hasAuthority('IVS_CLAIM_CONFIRM')")
    public ClaimApi.ClaimView resolve(@PathVariable("id") UUID id,
                                      @PathVariable("differenceId") UUID differenceId,
                                      @RequestBody(required = false) ClaimApi.ResolveDifferenceCommand command) {
        return service.resolveDifference(id, differenceId, command);
    }

    @PostMapping("/oem-claims/{id}/close")
    @PreAuthorize("hasAuthority('IVS_CLAIM_CONFIRM')")
    public ClaimApi.ClaimView close(@PathVariable("id") UUID id) {
        return service.close(id);
    }

    @PostMapping("/oem-claims")
    @PreAuthorize("hasAuthority('IVS_CLAIM_SUBMIT')")
    public ResponseEntity<ClaimApi.ClaimView> createDraft(@Valid @RequestBody ClaimApi.ApplyClaimsCommand command) {
        List<ClaimApi.ClaimView> created = service.apply(command);
        ClaimApi.ClaimView first = created.get(0);
        return ResponseEntity.created(URI.create("/api/ivs/oem-claims/" + first.id())).body(first);
    }
}
