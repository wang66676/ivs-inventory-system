package com.ivs.procurement.payable.api;

import com.ivs.procurement.payable.PayableService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ivs/payables")
public class PayableController {
    private final PayableService service;

    public PayableController(PayableService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<PayableApi.PayableView> list(@RequestParam(required = false) UUID supplierId,
                                             @RequestParam(required = false) com.ivs.procurement.payable.PayableStatus status) {
        return service.list(supplierId, status);
    }

    @PostMapping("/{id}/pay")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public PayableApi.PayableView pay(@PathVariable("id") UUID id,
                                      @RequestBody PayableApi.PayCommand command) {
        return service.pay(id, command);
    }
}
