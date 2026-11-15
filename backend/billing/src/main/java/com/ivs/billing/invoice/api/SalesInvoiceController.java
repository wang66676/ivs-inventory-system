package com.ivs.billing.invoice.api;

import com.ivs.billing.invoice.SalesInvoiceApplicationService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/** 销项发票：开票、PDF 识别、作废与红冲。 */
@RestController
@RequestMapping("/api/ivs/sales-invoices")
public class SalesInvoiceController {

    private final SalesInvoiceApplicationService service;

    public SalesInvoiceController(SalesInvoiceApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public List<BillingApi.InvoiceView> list(@RequestParam(required = false) UUID orderId,
                                             @RequestParam(required = false) UUID customerId) {
        return service.list(orderId, customerId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public BillingApi.InvoiceView get(@PathVariable("id") UUID id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('IVS_INVOICE_WRITE')")
    public ResponseEntity<BillingApi.InvoiceView> create(@Valid @RequestBody BillingApi.CreateInvoiceCommand command) {
        BillingApi.InvoiceView view = service.create(command);
        return ResponseEntity.created(URI.create("/api/ivs/sales-invoices/" + view.id())).body(view);
    }

    @PostMapping(value = "/pdf/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('IVS_INVOICE_WRITE')")
    public BillingApi.PdfExtractionView extractPdf(@RequestPart("file") MultipartFile file) {
        return service.extractPdf(file);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAuthority('IVS_FINANCE_READ')")
    public ResponseEntity<Resource> downloadPdf(@PathVariable("id") UUID id) {
        String fileName = service.pdfFileName(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName == null || fileName.isBlank() ? id + ".pdf" : fileName,
                        StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(service.pdf(id));
    }

    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('IVS_INVOICE_WRITE')")
    public BillingApi.InvoiceView issue(@PathVariable("id") UUID id,
                                        @Valid @RequestBody BillingApi.IssueInvoiceCommand command) {
        return service.issue(id, command);
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('IVS_INVOICE_WRITE')")
    public BillingApi.InvoiceView voidInvoice(@PathVariable("id") UUID id) {
        return service.voidInvoice(id);
    }

    @PostMapping("/{id}/red-flush")
    @PreAuthorize("hasAuthority('IVS_INVOICE_REDFLUSH')")
    public BillingApi.InvoiceView redFlush(@PathVariable("id") UUID id) {
        return service.redFlush(id);
    }
}