package com.ivs.policy.report;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 政策成本与返利报表。 */
@RestController
@RequestMapping("/api/ivs/policies/reports")
public class PolicyReportController {

    private final PolicyReportService service;

    public PolicyReportController(PolicyReportService service) {
        this.service = service;
    }

    @GetMapping("/receivable-aging")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<PolicyReportApi.ReceivableAgingRow> receivableAging(
            @RequestParam(required = false) UUID supplierId,
            @RequestParam(required = false) String periodCode) {
        return service.receivableAging(supplierId, periodCode);
    }

    @GetMapping("/pool-progress")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<PolicyReportApi.PoolProgressRow> poolProgress() {
        return service.poolProgress();
    }

    @GetMapping("/fund-ledger")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public PolicyReportApi.FundLedgerReport fundLedger(@RequestParam(required = false) UUID supplierId) {
        return service.fundLedger(supplierId);
    }

    @GetMapping("/unit-cost")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<PolicyReportApi.UnitCostRow> unitCost(@RequestParam(required = false) String periodCode) {
        return service.unitCost(periodCode);
    }

    @GetMapping("/cost-rate")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<PolicyReportApi.CostRateRow> costRate(@RequestParam(required = false) String periodCode) {
        return service.costRate(periodCode);
    }

    @GetMapping("/claim-differences")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<PolicyReportApi.ClaimDifferenceRow> claimDifferences(
            @RequestParam(required = false) UUID supplierId) {
        return service.claimDifferences(supplierId);
    }
}
