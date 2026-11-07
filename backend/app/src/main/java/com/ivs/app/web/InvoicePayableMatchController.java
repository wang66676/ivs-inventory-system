package com.ivs.app.web;

import com.ivs.app.orchestration.InvoicePayableMatchService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/ivs/purchase-invoice-matches")
@PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
public class InvoicePayableMatchController {
    private final InvoicePayableMatchService service;

    public InvoicePayableMatchController(InvoicePayableMatchService service) {
        this.service = service;
    }

    @PostMapping("/{invoiceId}/auto")
    @PreAuthorize("hasAuthority('IVS_INVOICE_WRITE')")
    public InvoicePayableMatchService.MatchResult autoMatch(@PathVariable UUID invoiceId) {
        return service.autoMatch(invoiceId);
    }

    @GetMapping("/{invoiceId}")
    public InvoicePayableMatchService.MatchResult matches(@PathVariable UUID invoiceId) {
        return service.matches(invoiceId);
    }
}
