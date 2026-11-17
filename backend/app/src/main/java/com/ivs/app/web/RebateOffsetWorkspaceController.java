package com.ivs.app.web;

import com.ivs.app.orchestration.RebateOffsetCancellationService;
import com.ivs.billing.purchase.PurchaseInvoice;
import com.ivs.billing.purchase.PurchaseInvoiceRepository;
import com.ivs.billing.purchase.api.PurchaseInvoiceApi;
import com.ivs.policy.fund.api.RebateFundApi;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** 返利抵扣工作台：取消抵扣时同步恢复采购收货与应付。 */
@RestController
@RequestMapping("/api/ivs/rebate-offset-workspace")
public class RebateOffsetWorkspaceController {

    private final RebateOffsetCancellationService service;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;

    public RebateOffsetWorkspaceController(RebateOffsetCancellationService service,
                                           PurchaseInvoiceRepository purchaseInvoiceRepository) {
        this.service = service;
        this.purchaseInvoiceRepository = purchaseInvoiceRepository;
    }

    @GetMapping("/red-invoices")
    @PreAuthorize("hasAuthority('IVS_FUND_OFFSET')")
    public List<PurchaseInvoiceApi.PurchaseInvoiceView> redInvoices(
            @RequestParam(required = false) UUID supplierId) {
        List<PurchaseInvoice> rows = supplierId == null
                ? purchaseInvoiceRepository.findAllByOrderByCreatedAtDesc()
                : purchaseInvoiceRepository.findBySupplierIdOrderByCreatedAtDesc(supplierId);
        return rows.stream().filter(row -> "RED".equals(row.getType()))
                .map(PurchaseInvoiceApi.PurchaseInvoiceView::from).toList();
    }

    @PostMapping("/{offsetId}/cancel")
    @PreAuthorize("hasAuthority('IVS_FUND_OFFSET')")
    public RebateFundApi.OffsetView cancel(@PathVariable("offsetId") UUID offsetId) {
        return service.cancel(offsetId);
    }
}
