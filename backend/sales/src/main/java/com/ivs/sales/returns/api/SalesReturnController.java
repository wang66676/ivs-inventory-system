package com.ivs.sales.returns.api;

import com.ivs.sales.returns.SalesReturnApplicationService;
import com.ivs.sales.returns.SalesReturnStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** 销售退货。 */
@RestController
@RequestMapping("/api/ivs/sales-returns")
public class SalesReturnController {

    private final SalesReturnApplicationService service;

    public SalesReturnController(SalesReturnApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<SalesReturnApi.ReturnView> list(@RequestParam(required = false) UUID customerId,
                                                 @RequestParam(required = false) SalesReturnStatus status) {
        return service.list(customerId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public SalesReturnApi.ReturnView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ResponseEntity<SalesReturnApi.ReturnView> create(
            @Valid @RequestBody SalesReturnApi.CreateReturnCommand command) {
        SalesReturnApi.ReturnView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/sales-returns/" + view.id())).body(view);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('IVS_STOCK_ADJUST')")
    public SalesReturnApi.ReturnView post(@PathVariable("id") UUID id) {
        return service.post(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public SalesReturnApi.ReturnView cancel(@PathVariable("id") UUID id) {
        return service.cancel(id);
    }
}
