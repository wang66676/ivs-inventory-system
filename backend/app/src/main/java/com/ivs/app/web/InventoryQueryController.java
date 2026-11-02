package com.ivs.app.web;

import com.ivs.app.orchestration.InventoryQueryService;
import com.ivs.inventory.stock.StockLockStatus;
import com.ivs.inventory.stock.StockMoveType;
import com.ivs.inventory.stock.api.InventoryApi;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 库存查询与看板（装配商品 / 仓库 / 序列号名称，放在 app 层保持模块边界）。 */
@RestController
@RequestMapping("/api/ivs/inventory")
public class InventoryQueryController {

    private final InventoryQueryService service;

    public InventoryQueryController(InventoryQueryService service) {
        this.service = service;
    }

    @GetMapping("/balances")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<InventoryApi.BalanceView> balances(@RequestParam(required = false) UUID warehouseId,
                                                   @RequestParam(required = false) String keyword) {
        return service.balances(warehouseId, keyword);
    }

    @GetMapping("/ledger")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<InventoryApi.LedgerView> ledger(@RequestParam(required = false) UUID productId,
                                                @RequestParam(required = false) UUID warehouseId,
                                                @RequestParam(required = false) StockMoveType moveType,
                                                @RequestParam(defaultValue = "200") int limit) {
        return service.ledger(productId, warehouseId, moveType, limit);
    }

    @GetMapping("/locks")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<InventoryApi.LockView> locks(@RequestParam(required = false) StockLockStatus status) {
        return service.locks(status);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public InventoryApi.SummaryView summary() {
        return service.summary();
    }
}
