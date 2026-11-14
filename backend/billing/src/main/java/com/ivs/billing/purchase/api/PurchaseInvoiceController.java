package com.ivs.billing.purchase.api;

import com.ivs.billing.purchase.PurchaseInvoiceApplicationService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** 进项发票：登记厂家红字（登记即入返利资金池）。 */
@RestController
@RequestMapping("/api/ivs/purchase-invoices")
public class PurchaseInvoiceController {

    private final PurchaseInvoiceApplicationService service;

    public PurchaseInvoiceController(PurchaseInvoiceApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<PurchaseInvoiceApi.PurchaseInvoiceView> list(@RequestParam(required = false) UUID supplierId) {
        return service.list(supplierId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public PurchaseInvoiceApi.PurchaseInvoiceView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping(value = "/pdf/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('IVS_INVOICE_WRITE', 'IVS_INVOICE_REDFLUSH')")
    public PurchaseInvoiceApi.PdfExtractionView extractPdf(@RequestPart("file") MultipartFile file) {
        return service.extractPdf(file);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public ResponseEntity<Resource> pdf(@PathVariable("id") UUID id) {
        Resource resource = service.pdf(id);
        String fileName = service.pdfFileName(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }

    @PostMapping("/red")
    @PreAuthorize("hasAuthority('IVS_INVOICE_REDFLUSH')")
    public ResponseEntity<PurchaseInvoiceApi.PurchaseInvoiceView> registerRed(
            @Valid @RequestBody PurchaseInvoiceApi.RegisterRedInvoiceCommand command) {
        PurchaseInvoiceApi.PurchaseInvoiceView view = service.registerRed(command);
        return ResponseEntity.created(URI.create("/api/ivs/purchase-invoices/" + view.id())).body(view);
    }

    @PostMapping("/blue")
    @PreAuthorize("hasAuthority('IVS_INVOICE_WRITE')")
    public ResponseEntity<PurchaseInvoiceApi.PurchaseInvoiceView> registerBlue(
            @Valid @RequestBody PurchaseInvoiceApi.RegisterBlueInvoiceCommand command) {
        PurchaseInvoiceApi.PurchaseInvoiceView view = service.registerBlue(command);
        return ResponseEntity.created(URI.create("/api/ivs/purchase-invoices/" + view.id())).body(view);
    }
}
