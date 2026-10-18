package com.ivs.masterdata.api;

import com.ivs.masterdata.SerialStatus;
import com.ivs.masterdata.serial.ProductSerialService;
import jakarta.validation.Valid;
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
import java.util.List;
import java.util.UUID;

/** 序列号档案：整车 VIN / 电池 SN 一物一档。 */
@RestController
@RequestMapping("/api/ivs/product-serials")
public class ProductSerialController {

    private final ProductSerialService service;

    public ProductSerialController(ProductSerialService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<MasterdataApi.SerialView> list(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) SerialStatus status,
                                               @RequestParam(required = false) UUID warehouseId) {
        return service.list(keyword, status, warehouseId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public MasterdataApi.SerialView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public ResponseEntity<MasterdataApi.SerialView> create(@Valid @RequestBody MasterdataApi.SaveSerialCommand command) {
        MasterdataApi.SerialView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/product-serials/" + view.id())).body(view);
    }

    @PostMapping("/batch-import")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.BatchImportResult importBatch(@RequestBody List<MasterdataApi.SaveSerialCommand> commands) {
        return service.importBatch(commands);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.SerialView update(@PathVariable("id") UUID id,
                                           @Valid @RequestBody MasterdataApi.SaveSerialCommand command) {
        return service.update(id, command);
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('IVS_PRODUCT_MANAGE')")
    public MasterdataApi.SerialView moveStatus(@PathVariable("id") UUID id,
                                               @Valid @RequestBody MasterdataApi.MoveSerialStatusCommand command) {
        return service.moveStatus(id, command.targetStatus());
    }
}