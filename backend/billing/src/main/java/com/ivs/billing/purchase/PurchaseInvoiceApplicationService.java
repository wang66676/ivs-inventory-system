package com.ivs.billing.purchase;

import com.ivs.billing.purchase.api.PurchaseInvoiceApi;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.event.EventEnvelope;
import com.ivs.shared.event.EventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 进项（厂家）红字发票登记：先冲减对应厂家应付，未冲减完的余额再进入返利资金池，
 * 后续可用于其他采购收货抵扣。
 */
@Service
public class PurchaseInvoiceApplicationService {

    private final PurchaseInvoiceRepository repository;
    private final RebateCreditPort rebateCreditPort;
    private final PayableCreditPort payableCreditPort;
    private final ClaimInvoicePort claimInvoicePort;
    private final PurchaseInvoiceRebateLineRepository rebateLineRepository;
    private final PurchaseInvoicePdfService pdfService;
    private final EventPublisher events;

    public PurchaseInvoiceApplicationService(PurchaseInvoiceRepository repository,
                                             RebateCreditPort rebateCreditPort,
                                             PayableCreditPort payableCreditPort,
                                             ClaimInvoicePort claimInvoicePort,
                                             PurchaseInvoiceRebateLineRepository rebateLineRepository,
                                             PurchaseInvoicePdfService pdfService,
                                             EventPublisher events) {
        this.repository = repository;
        this.rebateCreditPort = rebateCreditPort;
        this.payableCreditPort = payableCreditPort;
        this.claimInvoicePort = claimInvoicePort;
        this.rebateLineRepository = rebateLineRepository;
        this.pdfService = pdfService;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<PurchaseInvoiceApi.PurchaseInvoiceView> list(UUID supplierId) {
        List<PurchaseInvoice> rows = supplierId == null ? repository.findAllByOrderByCreatedAtDesc()
                : repository.findBySupplierIdOrderByCreatedAtDesc(supplierId);
        return rows.stream().map(PurchaseInvoiceApi.PurchaseInvoiceView::from).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseInvoiceApi.PurchaseInvoiceView get(UUID id) {
        return repository.findById(id).map(PurchaseInvoiceApi.PurchaseInvoiceView::from)
                .orElseThrow(() -> new NotFoundException("进项发票", id));
    }

    /** 登记厂家红字发票：先自动冲减应付，剩余部分进入返利资金池。 */
    @Transactional
    public PurchaseInvoiceApi.PurchaseInvoiceView registerRed(
            PurchaseInvoiceApi.RegisterRedInvoiceCommand command) {
        if (command.supplierId() == null) {
            throw new ConflictException("SUPPLIER_REQUIRED", "请指定开票厂家");
        }
        List<String> serialNos = command.serialNos() == null ? List.of() : command.serialNos().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase()).distinct().toList();
        ClaimInvoicePort.ClaimInvoiceInfo claimInfo = command.claimId() == null ? null
                : claimInvoicePort.validateForInvoice(command.claimId(), command.supplierId(), command.amount(),
                serialNos);
        PurchaseInvoice invoice;
        try {
            invoice = PurchaseInvoice.registerRed(
                    "PINV-R-%06d".formatted(repository.nextInvoiceSequence()), command.supplierId(),
                    command.amount(), command.taxRate(), command.originalInvoiceNo(),
                    command.externalInvoiceNo(), String.join(",", serialNos), command.claimId(),
                    command.issuedAt(), command.remark());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("PURCHASE_INVOICE_INVALID", exception.getMessage());
        }
        PurchaseInvoice saved = repository.save(invoice);
        attachPdf(saved, command.pdfFileName(), command.pdfStorageKey(), command.extractedText());
        saved = repository.save(saved);
        if (saved.getClaimId() != null) {
            claimInvoicePort.settleFromInvoice(saved.getClaimId(), saved.getId(), saved.getInvoiceNo());
        }
        List<PayableCreditPort.RebateLine> rebateLines = claimInfo == null ? List.of()
                : claimInfo.allocations().stream().map(item -> new PayableCreditPort.RebateLine(
                        item.serialNo(), item.purchaseOrderId(), item.purchaseReceiptId(), item.confirmedAmount())).toList();
        if (claimInfo != null) {
            UUID invoiceId = saved.getId();
            UUID claimId = saved.getClaimId();
            rebateLineRepository.saveAll(claimInfo.allocations().stream()
                    .map(item -> PurchaseInvoiceRebateLine.create(invoiceId, claimId, item.claimLineId(),
                            item.entitlementId(), item.serialNo(), item.purchaseOrderId(), item.purchaseReceiptId(),
                            item.confirmedAmount())).toList());
        }
        PayableCreditPort.CreditResult credit = rebateLines.isEmpty()
                ? new PayableCreditPort.CreditResult(BigDecimal.ZERO.setScale(2), saved.getAmount())
                : payableCreditPort.apply(saved.getSupplierId(), rebateLines, saved.getClaimId(),
                saved.getInvoiceNo());
        if (credit.unappliedAmount().signum() > 0) {
            rebateCreditPort.credit(saved.getSupplierId(), credit.unappliedAmount(), "RED_INVOICE",
                    saved.getInvoiceNo(), saved.getId(), "政策红字冲减应付后的剩余返利入池");
        }
        saved.recordSettlement(credit.appliedAmount(), credit.unappliedAmount());
        saved = repository.save(saved);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invoiceNo", saved.getInvoiceNo());
        payload.put("supplierId", saved.getSupplierId().toString());
        payload.put("amount", saved.getAmount());
        payload.put("payableAppliedAmount", saved.getPayableAppliedAmount());
        payload.put("fundPoolAmount", saved.getFundPoolAmount());
        payload.put("taxAmount", saved.getTaxAmount());
        events.publish(new EventEnvelope(UUID.randomUUID(), "PurchaseInvoiceRegistered", "PurchaseInvoice",
                saved.getId(), saved.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
        return PurchaseInvoiceApi.PurchaseInvoiceView.from(saved);
    }

    public PurchaseInvoiceApi.PdfExtractionView extractPdf(org.springframework.web.multipart.MultipartFile file) {
        return pdfService.extract(file);
    }

    public org.springframework.core.io.Resource pdf(UUID id) {
        PurchaseInvoice invoice = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("进项发票", id));
        if (invoice.getPdfStorageKey() == null) {
            throw new NotFoundException("电子发票附件", id);
        }
        return pdfService.resource(invoice.getPdfStorageKey());
    }

    public String pdfFileName(UUID id) {
        return repository.findById(id).map(PurchaseInvoice::getPdfFileName)
                .orElseThrow(() -> new NotFoundException("进项发票", id));
    }

    private void attachPdf(PurchaseInvoice invoice, String fileName, String storageKey, String extractedText) {
        if (storageKey == null || storageKey.isBlank()) return;
        pdfService.validatedKey(storageKey);
        pdfService.resource(storageKey);
        String safeName = fileName == null || fileName.isBlank() ? storageKey : fileName;
        invoice.attachPdf(safeName, storageKey, extractedText);
    }

    /** 登记厂家蓝字进项发票，用于与供应商应付匹配。 */
    @Transactional
    public PurchaseInvoiceApi.PurchaseInvoiceView registerBlue(
            PurchaseInvoiceApi.RegisterBlueInvoiceCommand command) {
        if (command.supplierId() == null) {
            throw new ConflictException("SUPPLIER_REQUIRED", "请指定开票厂家");
        }
        PurchaseInvoice invoice;
        try {
            String serialNos = command.serialNos() == null ? null : command.serialNos().stream()
                    .map(String::trim).filter(value -> !value.isBlank()).distinct()
                    .collect(java.util.stream.Collectors.joining(","));
            invoice = PurchaseInvoice.registerBlue(
                    "PINV-B-%06d".formatted(repository.nextInvoiceSequence()), command.supplierId(),
                    command.purchaseOrderId(), serialNos, command.amount(), command.taxRate(),
                    command.invoiceType(), command.externalInvoiceNo(), command.issuedAt(), command.remark());
        } catch (IllegalArgumentException exception) {
            throw new ConflictException("PURCHASE_INVOICE_INVALID", exception.getMessage());
        }
        PurchaseInvoice saved = repository.save(invoice);
        attachPdf(saved, command.pdfFileName(), command.pdfStorageKey(), command.extractedText());
        saved = repository.save(saved);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("invoiceNo", saved.getInvoiceNo());
        payload.put("supplierId", saved.getSupplierId().toString());
        payload.put("purchaseOrderId", saved.getPurchaseOrderId());
        payload.put("serialNos", saved.getSerialNos());
        payload.put("amount", saved.getAmount());
        payload.put("taxAmount", saved.getTaxAmount());
        events.publish(new EventEnvelope(UUID.randomUUID(), "PurchaseInvoiceRegistered", "PurchaseInvoice",
                saved.getId(), saved.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
        return PurchaseInvoiceApi.PurchaseInvoiceView.from(saved);
    }
}
