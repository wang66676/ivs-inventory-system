package com.ivs.inventory.transfer.api;

import com.ivs.inventory.transfer.StockTransferApplicationService;
import com.ivs.inventory.transfer.StockTransferStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** 库存调拨：草稿、审核、在途、签收与差异。 */
@RestController
@RequestMapping("/api/ivs/transfers")
public class StockTransferController {

    private final StockTransferApplicationService service;

    public StockTransferController(StockTransferApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<StockTransferApi.TransferView> list(@RequestParam(required = false) UUID warehouseId,
                                                    @RequestParam(required = false) StockTransferStatus status) {
        return service.list(warehouseId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public StockTransferApi.TransferView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_STOCK_TRANSFER')")
    public ResponseEntity<StockTransferApi.TransferView> create(
            @Valid @RequestBody StockTransferApi.CreateTransferCommand command) {
        StockTransferApi.TransferView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/transfers/" + view.id())).body(view);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_STOCK_TRANSFER')")
    public StockTransferApi.TransferView update(@PathVariable("id") UUID id,
                                                 @Valid @RequestBody StockTransferApi.UpdateTransferCommand command) {
        return service.update(id, command);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('IVS_STOCK_TRANSFER')")
    public StockTransferApi.TransferView approve(@PathVariable("id") UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasAuthority('IVS_STOCK_TRANSFER')")
    public StockTransferApi.TransferView dispatch(@PathVariable("id") UUID id) {
        return service.dispatch(id);
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('IVS_STOCK_TRANSFER')")
    public StockTransferApi.TransferView receive(@PathVariable("id") UUID id,
                                                  @Valid @RequestBody StockTransferApi.ReceiveTransferCommand command) {
        return service.receive(id, command);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('IVS_STOCK_TRANSFER')")
    public StockTransferApi.TransferView cancel(@PathVariable("id") UUID id) {
        return service.cancel(id);
    }
}
