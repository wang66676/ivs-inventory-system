package com.ivs.lease.api;

import com.ivs.lease.LeaseApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ivs/lease-contracts")
public class LeaseController {
    private final LeaseApplicationService service;

    public LeaseController(LeaseApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE') or hasAuthority('IVS_FINANCE_READ')")
    public List<LeaseApi.LeaseContractView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE') or hasAuthority('IVS_FINANCE_READ')")
    public LeaseApi.LeaseContractView get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE')")
    public ResponseEntity<LeaseApi.LeaseContractView> create(@Valid @RequestBody LeaseApi.CreateLeaseCommand command) {
        LeaseApi.LeaseContractView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/lease-contracts/" + view.id())).body(view);
    }

    @PostMapping("/{id}/generate-due")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE')")
    public LeaseApi.LeaseContractView generateDue(@PathVariable UUID id,
                                                  @RequestParam(required = false) LocalDate throughDate) {
        return service.generateDue(id, throughDate);
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE')")
    public LeaseApi.LeaseContractView end(@PathVariable UUID id) {
        return service.end(id);
    }
}
