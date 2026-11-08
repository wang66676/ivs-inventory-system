package com.ivs.lease.api;

import com.ivs.lease.LeaseDisposalService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ivs/lease-assets")
public class LeaseDisposalController {
    private final LeaseDisposalService service;

    public LeaseDisposalController(LeaseDisposalService service) {
        this.service = service;
    }

    @GetMapping("/disposals")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE') or hasAuthority('IVS_FINANCE_READ')")
    public List<LeaseApi.AssetDisposalView> list(@RequestParam(required = false) UUID contractId) {
        return service.list(contractId);
    }

    @PostMapping("/{serialId}/retire")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE')")
    public LeaseApi.AssetDisposalView retire(@PathVariable UUID serialId,
                                             @Valid @RequestBody LeaseApi.RetireAssetCommand command) {
        return service.retire(serialId, command);
    }
}
