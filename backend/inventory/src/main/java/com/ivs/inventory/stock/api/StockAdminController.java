package com.ivs.inventory.stock.api;

import com.ivs.inventory.stock.StockLedger;
import com.ivs.inventory.stock.StockReconciliationService;
import com.ivs.inventory.stock.StockService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/** 库存运维接口：锁定 / 调整 / 对账（列表与看板见 app 层的装配接口）。 */
@RestController
@RequestMapping("/api/ivs/stocks")
public class StockAdminController {

    private final StockService stockService;
    private final StockReconciliationService reconciliationService;

    public StockAdminController(StockService stockService, StockReconciliationService reconciliationService) {
        this.stockService = stockService;
        this.reconciliationService = reconciliationService;
    }

    @PostMapping("/locks")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ResponseEntity<InventoryApi.LockView> lock(@Valid @RequestBody InventoryApi.CreateLockCommand command) {
        var lock = stockService.lock(command.productId(), command.serialId(), command.warehouseId(),
                command.batchNo(), command.quantity(), command.bizType(), command.bizId(), command.bizNo(),
                command.validDays() == null ? null : Duration.ofDays(command.validDays()));
        return ResponseEntity.created(URI.create("/api/ivs/stocks/locks/" + lock.getId()))
                .body(InventoryApi.LockView.from(lock));
    }

    @PostMapping("/locks/{lockId}/release")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public InventoryApi.LockView release(@PathVariable("lockId") UUID lockId) {
        return InventoryApi.LockView.from(stockService.release(lockId));
    }

    @PostMapping("/adjust")
    @PreAuthorize("hasAuthority('IVS_STOCK_ADJUST')")
    public InventoryApi.LedgerView adjust(@Valid @RequestBody InventoryApi.AdjustStockCommand command) {
        StockLedger ledger = stockService.adjustTo(command.productId(), command.warehouseId(), command.batchNo(),
                command.targetQuantity(), command.targetUnitCost(), "MANUAL_ADJUST", null, null, command.remark());
        return InventoryApi.LedgerView.from(ledger);
    }

    /** 台账与余额对账：返回不一致条目（正常情况下为空）。 */
    @GetMapping("/reconciliation")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public InventoryApi.ReconciliationView reconciliation() {
        List<InventoryApi.DifferenceView> differences = reconciliationService.reconcile().stream()
                .map(item -> new InventoryApi.DifferenceView(item.productId(), null, item.warehouseId(), null,
                        item.batchNo(), item.balanceQty(), item.ledgerQty()))
                .toList();
        return new InventoryApi.ReconciliationView(differences.isEmpty(), reconciliationService.ledgerCount(),
                differences);
    }

    /** 手工触发超时锁定释放（定时任务同样会执行）。 */
    @PostMapping("/locks/release-expired")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public int releaseExpired() {
        return stockService.releaseExpired();
    }

}