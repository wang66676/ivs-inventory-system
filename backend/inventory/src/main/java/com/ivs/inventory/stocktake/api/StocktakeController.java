package com.ivs.inventory.stocktake.api;

import com.ivs.inventory.stocktake.StocktakeApplicationService;
import com.ivs.inventory.stocktake.StocktakeStatus;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/** 库存盘点：创建账面快照、实盘录入、差异过账。 */
@RestController
@RequestMapping("/api/ivs/stocktakes")
public class StocktakeController {

    private final StocktakeApplicationService service;

    public StocktakeController(StocktakeApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<StocktakeApi.StocktakeView> list(@RequestParam(required = false) UUID warehouseId,
                                                 @RequestParam(required = false) StocktakeStatus status) {
        return service.list(warehouseId, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public StocktakeApi.StocktakeView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_STOCK_ADJUST')")
    public ResponseEntity<StocktakeApi.StocktakeView> create(
            @Valid @RequestBody StocktakeApi.CreateStocktakeCommand command) {
        StocktakeApi.StocktakeView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/stocktakes/" + view.id())).body(view);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_STOCK_ADJUST')")
    public StocktakeApi.StocktakeView update(@PathVariable("id") UUID id,
                                             @Valid @RequestBody StocktakeApi.UpdateStocktakeCommand command) {
        return service.update(id, command);
    }

    @PostMapping("/{id}/post")
    @PreAuthorize("hasAuthority('IVS_STOCK_ADJUST')")
    public StocktakeApi.StocktakeView post(@PathVariable("id") UUID id) {
        return service.post(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('IVS_STOCK_ADJUST')")
    public StocktakeApi.StocktakeView cancel(@PathVariable("id") UUID id) {
        return service.cancel(id);
    }
}
