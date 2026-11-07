package com.ivs.billing.refund.api;

import com.ivs.billing.refund.RefundApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ivs/refunds")
public class RefundController {
    private final RefundApplicationService service;

    public RefundController(RefundApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<RefundApi.RefundView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public RefundApi.RefundView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ResponseEntity<RefundApi.RefundView> create(@RequestBody RefundApi.CreateRefundCommand command) {
        RefundApi.RefundView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/refunds/" + view.id())).body(view);
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('IVS_REFUND_MANAGE')")
    public RefundApi.RefundView execute(@PathVariable UUID id,
                                        @RequestBody RefundApi.ExecuteRefundCommand command) {
        return service.execute(id, command);
    }

    @PostMapping("/{id}/manual-complete")
    @PreAuthorize("hasAuthority('IVS_REFUND_MANAGE')")
    public RefundApi.RefundView manualComplete(@PathVariable UUID id,
                                               @RequestBody(required = false) RefundApi.ManualCompleteCommand command) {
        return service.manualComplete(id, command);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public RefundApi.RefundView cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
