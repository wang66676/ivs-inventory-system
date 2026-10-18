package com.ivs.procurement.purchase.api;

import com.ivs.procurement.purchase.PurchaseOrderApplicationService;
import com.ivs.procurement.purchase.PurchaseOrderStatus;
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
import java.util.List;
import java.util.UUID;

/** 采购单与收货。 */
@RestController
@RequestMapping("/api/ivs/purchase-orders")
public class PurchaseOrderController {

    private final PurchaseOrderApplicationService service;

    public PurchaseOrderController(PurchaseOrderApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<ProcurementApi.PurchaseOrderView> list(@RequestParam(required = false) String keyword,
                                                       @RequestParam(required = false) PurchaseOrderStatus status) {
        return service.list(keyword, status);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public ProcurementApi.PurchaseOrderView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ResponseEntity<ProcurementApi.PurchaseOrderView> create(
            @Valid @RequestBody ProcurementApi.SavePurchaseOrderCommand command) {
        ProcurementApi.PurchaseOrderView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/purchase-orders/" + view.id())).body(view);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ProcurementApi.PurchaseOrderView approve(@PathVariable("id") UUID id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ProcurementApi.PurchaseOrderView cancel(@PathVariable("id") UUID id) {
        return service.cancel(id);
    }

    /** 分批收货：序列号商品按 VIN 收货，数量商品按数量收货。 */
    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ProcurementApi.PurchaseReceiptView receive(@PathVariable("id") UUID id,
                                                      @Valid @RequestBody ProcurementApi.ReceivePurchaseOrderCommand command) {
        return service.receive(id, command);
    }
}