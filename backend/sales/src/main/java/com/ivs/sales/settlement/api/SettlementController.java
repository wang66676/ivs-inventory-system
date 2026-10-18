package com.ivs.sales.settlement.api;

import com.ivs.sales.order.api.SalesApi;
import com.ivs.sales.settlement.ReceiptApplicationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 应收、收款与账龄。 */
@RestController
@RequestMapping("/api/ivs")
public class SettlementController {

    private final ReceiptApplicationService service;

    public SettlementController(ReceiptApplicationService service) {
        this.service = service;
    }

    @GetMapping("/receivables")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<SalesApi.ReceivableView> receivables(@RequestParam(required = false) UUID customerId) {
        return service.listReceivables(customerId);
    }

    @GetMapping("/receivables/aging")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<SalesApi.AgingBucketView> aging() {
        return service.aging();
    }

    @GetMapping("/receipts")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<SalesApi.ReceiptView> receipts() {
        return service.listReceipts();
    }

    /** 登记收款：按到期日 FIFO 自动核销应收。 */
    @PostMapping("/receipts")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public SalesApi.ReceiptView createReceipt(@Valid @RequestBody SalesApi.CreateReceiptCommand command) {
        return service.createReceipt(command);
    }
}