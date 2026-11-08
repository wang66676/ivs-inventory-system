package com.ivs.lease.api;

import com.ivs.lease.LeaseReportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ivs/lease-assets")
public class LeaseReportController {
    private final LeaseReportService service;

    public LeaseReportController(LeaseReportService service) {
        this.service = service;
    }

    @GetMapping("/report")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE') or hasAuthority('IVS_FINANCE_READ')")
    public LeaseApi.LeaseAssetReportView report() {
        return service.report();
    }
}
