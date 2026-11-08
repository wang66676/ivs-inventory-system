package com.ivs.lease.api;

import com.ivs.lease.LeaseApplicationService;
import com.ivs.lease.LeaseHealthService;
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
    private final LeaseHealthService healthService;

    public LeaseController(LeaseApplicationService service, LeaseHealthService healthService) {
        this.service = service;
        this.healthService = healthService;
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

    @PostMapping("/generate-due-batch")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE')")
    public LeaseApi.DueGenerationResult generateDueBatch(@RequestParam(required = false) LocalDate throughDate) {
        return service.generateDueBatch(throughDate);
    }

    @PostMapping("/run-automation")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE')")
    public LeaseApi.AutomationResult runAutomation(@RequestParam(required = false) LocalDate asOfDate,
                                                   @RequestParam(required = false) Integer reminderDays) {
        return service.runAutomation(asOfDate, reminderDays);
    }

    @PostMapping("/{id}/end")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE')")
    public LeaseApi.LeaseContractView end(@PathVariable UUID id,
                                          @RequestBody(required = false) LeaseApi.EndLeaseCommand command) {
        return service.end(id, command);
    }

    @PostMapping("/{id}/deposits/collect")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE')")
    public LeaseApi.LeaseContractView collectDeposit(@PathVariable UUID id,
                                                     @Valid @RequestBody LeaseApi.DepositCommand command) {
        return service.collectDeposit(id, command);
    }

    @PostMapping("/{id}/deposits/refund")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE')")
    public LeaseApi.LeaseContractView refundDeposit(@PathVariable UUID id,
                                                    @Valid @RequestBody LeaseApi.DepositCommand command) {
        return service.refundDeposit(id, command);
    }

    @GetMapping("/{id}/health-records")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE') or hasAuthority('IVS_FINANCE_READ')")
    public List<LeaseApi.HealthRecordView> listHealthRecords(@PathVariable UUID id) {
        return healthService.list(id);
    }

    @PostMapping("/{id}/health-records")
    @PreAuthorize("hasAuthority('IVS_LEASE_MANAGE')")
    public LeaseApi.HealthRecordView createHealthRecord(@PathVariable UUID id,
                                                        @Valid @RequestBody LeaseApi.HealthRecordCommand command) {
        return healthService.create(id, command);
    }
}
