package com.ivs.procurement.returns.api;

import com.ivs.procurement.returns.PurchaseReturnApplicationService;
import com.ivs.procurement.returns.PurchaseReturnStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** 采购退货。 */
@RestController
@RequestMapping("/api/ivs/purchase-returns")
public class PurchaseReturnController {

    private final PurchaseReturnApplicationService service;

    public PurchaseReturnController(PurchaseReturnApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<PurchaseReturnApi.ReturnView> list(@RequestParam(required = false) UUID supplierId,
                                                    @RequestParam(required = false) PurchaseReturnStatus status) {
        return service.list(supplierId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public PurchaseReturnApi.ReturnView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ResponseEntity<PurchaseReturnApi.ReturnView> create(
            @Valid @RequestBody PurchaseReturnApi.CreateReturnCommand command) {
        PurchaseReturnApi.ReturnView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/purchase-returns/" + view.id())).body(view);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('IVS_STOCK_ADJUST')")
    public PurchaseReturnApi.ReturnView post(@PathVariable("id") UUID id) {
        return service.post(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public PurchaseReturnApi.ReturnView cancel(@PathVariable("id") UUID id) {
        return service.cancel(id);
    }
}
