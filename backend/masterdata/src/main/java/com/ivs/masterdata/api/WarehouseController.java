package com.ivs.masterdata.api;

import com.ivs.masterdata.warehouse.WarehouseService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ivs/warehouses")
public class WarehouseController {

    private final WarehouseService service;

    public WarehouseController(WarehouseService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<MasterdataApi.WarehouseView> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public MasterdataApi.WarehouseView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MasterdataApi.WarehouseView> create(@Valid @RequestBody MasterdataApi.SaveWarehouseCommand command) {
        MasterdataApi.WarehouseView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/warehouses/" + view.id())).body(view);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public MasterdataApi.WarehouseView update(@PathVariable("id") UUID id,
                                              @Valid @RequestBody MasterdataApi.SaveWarehouseCommand command) {
        return service.update(id, command);
    }

    @PostMapping("/{id}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    public MasterdataApi.WarehouseView disable(@PathVariable("id") UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    public MasterdataApi.WarehouseView enable(@PathVariable("id") UUID id) {
        return service.enable(id);
    }
}
