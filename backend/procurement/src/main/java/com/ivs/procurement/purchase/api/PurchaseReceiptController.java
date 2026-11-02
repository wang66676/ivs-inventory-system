package com.ivs.procurement.purchase.api;

import com.ivs.procurement.purchase.PurchaseOrderApplicationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 收货单查询。 */
@RestController
@RequestMapping("/api/ivs/purchase-receipts")
public class PurchaseReceiptController {

    private final PurchaseOrderApplicationService service;

    public PurchaseReceiptController(PurchaseOrderApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_READ')")
    public List<ProcurementApi.PurchaseReceiptView> list() {
        return service.listReceipts();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_READ')")
    public ProcurementApi.PurchaseReceiptView get(@PathVariable("id") UUID id) {
        return service.getReceipt(id);
    }

    @PutMapping("/{receiptId}/lines/{lineId}/certificate-application-date")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ProcurementApi.PurchaseReceiptView updateCertificateApplicationDate(
            @PathVariable("receiptId") UUID receiptId,
            @PathVariable("lineId") UUID lineId,
            @RequestBody ProcurementApi.UpdateCertificateDateCommand command) {
        return service.updateCertificateApplicationDate(receiptId, lineId, command.certificateApplicationDate());
    }

    @PutMapping("/{receiptId}/lines/{lineId}/warehouse")
    @PreAuthorize("hasAuthority('IVS_WRITE')")
    public ProcurementApi.PurchaseReceiptView updateReceiptWarehouse(
            @PathVariable("receiptId") UUID receiptId,
            @PathVariable("lineId") UUID lineId,
            @RequestBody ProcurementApi.UpdateReceiptWarehouseCommand command) {
        return service.updateReceiptWarehouse(receiptId, lineId, command.warehouseId());
    }
}
