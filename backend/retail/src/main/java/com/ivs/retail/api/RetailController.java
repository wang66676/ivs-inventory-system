package com.ivs.retail.api;

import com.ivs.retail.retail.RetailApplicationService;
import com.ivs.retail.retail.RetailStatus;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** 实销登记、复核与冲正。 */
@RestController
@RequestMapping("/api/ivs/retails")
public class RetailController {

    private final RetailApplicationService service;

    public RetailController(RetailApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<RetailApi.RetailView> list(@RequestParam(required = false) RetailStatus status,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.list(status, from, to);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public RetailApi.RetailView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_RETAIL_WRITE')")
    public ResponseEntity<RetailApi.RetailView> create(@Valid @RequestBody RetailApi.SaveRetailCommand command) {
        RetailApi.RetailView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/retails/" + view.id())).body(view);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_RETAIL_WRITE')")
    public RetailApi.RetailView update(@PathVariable("id") UUID id,
                                       @Valid @RequestBody RetailApi.SaveRetailCommand command) {
        return service.update(id, command);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('IVS_RETAIL_WRITE')")
    public RetailApi.RetailView submit(@PathVariable("id") UUID id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAuthority('IVS_RETAIL_VERIFY')")
    public RetailApi.RetailView verify(@PathVariable("id") UUID id) {
        return service.verify(id);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('IVS_RETAIL_VERIFY')")
    public RetailApi.RetailView reverse(@PathVariable("id") UUID id,
                                        @Valid @RequestBody RetailApi.ReverseRetailCommand command) {
        return service.reverse(id, command);
    }
}