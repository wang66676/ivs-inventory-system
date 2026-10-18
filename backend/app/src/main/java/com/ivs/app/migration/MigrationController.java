package com.ivs.app.migration;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** 历史数据迁移：试运行校验、导入、对账、回滚。 */
@RestController
@RequestMapping("/api/ivs/migrations")
@PreAuthorize("hasAuthority('USER_MANAGE')")
public class MigrationController {

    private final MigrationService service;

    public MigrationController(MigrationService service) {
        this.service = service;
    }

    @PostMapping("/validate")
    public MigrationApi.ValidationView validate(@RequestBody MigrationApi.Payload payload) {
        return service.validate(payload);
    }

    @PostMapping("/import")
    public ResponseEntity<MigrationApi.BatchView> importBatch(@Valid @RequestBody MigrationApi.Payload payload) {
        MigrationApi.BatchView batch = service.importBatch(payload);
        return ResponseEntity.created(URI.create("/api/ivs/migrations/" + batch.id())).body(batch);
    }

    @GetMapping
    public List<MigrationApi.BatchView> list() {
        return service.listBatches();
    }

    @GetMapping("/{id}")
    public MigrationApi.BatchView get(@PathVariable("id") UUID id) {
        return service.getBatch(id);
    }

    @GetMapping("/{id}/reconciliation")
    public MigrationApi.ReconciliationView reconciliation(@PathVariable("id") UUID id) {
        return service.reconcile(id);
    }

    @PostMapping("/{id}/rollback")
    public MigrationApi.BatchView rollback(@PathVariable("id") UUID id) {
        return service.rollback(id);
    }
}
