package com.ivs.app.report;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/** 经营报表：月报、毛利、库存资金占用。 */
@RestController
@RequestMapping("/api/ivs/reports")
public class ReportController {

    private final ReportQueryService service;

    public ReportController(ReportQueryService service) {
        this.service = service;
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public ReportApi.MonthlyReport monthly(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.monthly(from, to);
    }

    @GetMapping("/gross-profit")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public ReportApi.GrossProfitReport grossProfit(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.grossProfit(from, to);
    }

    @GetMapping("/stock-funds")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public ReportApi.StockFundReport stockFunds(@RequestParam(required = false) UUID warehouseId) {
        return service.stockFunds(warehouseId);
    }
}
