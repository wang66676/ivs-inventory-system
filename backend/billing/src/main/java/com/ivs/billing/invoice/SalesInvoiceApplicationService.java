package com.ivs.billing.invoice;

import com.ivs.billing.invoice.api.BillingApi;
import com.ivs.billing.purchase.PurchaseInvoicePdfService;
import com.ivs.shared.error.ConflictException;
import com.ivs.shared.error.NotFoundException;
import com.ivs.shared.event.EventEnvelope;
import com.ivs.shared.event.EventPublisher;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 销项发票：开票、作废、红冲（红字发票必须关联原蓝票）。 */
@Service
public class SalesInvoiceApplicationService {

    private final SalesInvoiceRepository repository;
    private final BillingReferencePort referencePort;
    private final EventPublisher events;
    private final PurchaseInvoicePdfService pdfService;

    public SalesInvoiceApplicationService(SalesInvoiceRepository repository,
                                          BillingReferencePort referencePort,
                                          EventPublisher events,
                                          PurchaseInvoicePdfService pdfService) {
        this.repository = repository;
        this.referencePort = referencePort;
        this.events = events;
        this.pdfService = pdfService;
    }

    @Transactional(readOnly = true)
    public List<BillingApi.InvoiceView> list(UUID orderId, UUID customerId) {
        List<SalesInvoice> invoices = orderId != null ? repository.findByOrderIdOrderByCreatedAtDesc(orderId)
                : customerId != null ? repository.findByCustomerIdOrderByCreatedAtDesc(customerId)
                : repository.findAllByOrderByCreatedAtDesc();
        return invoices.stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public BillingApi.InvoiceView get(UUID id) {
        return toView(find(id));
    }

    @Transactional
    public BillingApi.InvoiceView create(BillingApi.CreateInvoiceCommand command) {
        UUID customerId = command.customerId();
        BigDecimal amount = command.amount();
        if (command.orderId() != null) {
            BillingReferencePort.OrderInfo order = referencePort.requireOrder(command.orderId());
            customerId = order.customerId();
            if (amount == null) amount = order.deliveredAmount();
        }
        if (customerId == null) {
            throw new ConflictException("INVOICE_CUSTOMER_REQUIRED", "开票必须指定客户或关联销售订单");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new ConflictException("INVOICE_AMOUNT_REQUIRED", "开票金额必须大于 0");
        }
        SalesInvoice invoice = SalesInvoice.create(
                "INV-%06d".formatted(repository.nextInvoiceSequence()), InvoiceDirection.OUT, InvoiceType.BLUE,
                command.orderId(), command.deliveryId(), customerId, amount, command.taxRate(), command.remark());
        return toView(repository.save(invoice));
    }

    /** 直接登记销项蓝字发票，联动客户、销售订单、VIN/SN 与电子发票附件。 */
    @Transactional
    public BillingApi.InvoiceView registerBlue(BillingApi.RegisterSalesInvoiceCommand command) {
        if (command == null || command.orderId() == null) {
            throw new ConflictException("SALES_ORDER_REQUIRED", "请选择销售订单编号");
        }
        BillingReferencePort.OrderInfo order = referencePort.requireOrder(command.orderId());
        if (command.customerId() != null && !command.customerId().equals(order.customerId())) {
            throw new ConflictException("INVOICE_CUSTOMER_MISMATCH", "所选客户与销售订单客户不一致");
        }
        List<String> serialNos = normalizeSerialNos(command.serialNos());
        if (serialNos.isEmpty()) {
            throw new ConflictException("INVOICE_SERIAL_REQUIRED", "请至少选择一个 VIN/SN");
        }
        var serialOrder = referencePort.findOrderBySerialNumbers(serialNos);
        if (serialOrder.isEmpty() || !serialOrder.get().orderId().equals(order.orderId())) {
            throw new ConflictException("INVOICE_SERIAL_ORDER_MISMATCH", "所选 VIN/SN 不属于当前销售订单");
        }
        ensureSerialsNotInvoiced(order.orderId(), serialNos);
        if (command.externalInvoiceNo() == null || command.externalInvoiceNo().isBlank()) {
            throw new ConflictException("INVOICE_NUMBER_REQUIRED", "请填写发票号码");
        }
        if (command.invoiceType() == null || command.invoiceType().isBlank()) {
            throw new ConflictException("INVOICE_TYPE_REQUIRED", "请填写发票类型");
        }
        if (command.issuedAt() == null) {
            throw new ConflictException("INVOICE_DATE_REQUIRED", "请选择开票日期");
        }
        BigDecimal amount = command.amount() == null ? order.deliveredAmount() : command.amount();
        if (amount == null || amount.signum() <= 0) {
            throw new ConflictException("INVOICE_AMOUNT_REQUIRED", "发票金额必须大于 0");
        }
        SalesInvoice invoice = SalesInvoice.create(
                "INV-%06d".formatted(repository.nextInvoiceSequence()), InvoiceDirection.OUT, InvoiceType.BLUE,
                order.orderId(), null, order.customerId(), amount, command.taxRate(), command.remark());
        invoice.assignSerialNos(String.join(",", serialNos));
        try {
            if (command.pdfStorageKey() != null && !command.pdfStorageKey().isBlank()) {
                pdfService.validatedKey(command.pdfStorageKey());
                pdfService.resource(command.pdfStorageKey());
            }
            invoice.issue(command.externalInvoiceNo(), command.issuedAt(), command.invoiceType(),
                    command.pdfFileName(), command.pdfStorageKey(), command.extractedText());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ConflictException("INVOICE_REGISTER_INVALID", exception.getMessage());
        }
        SalesInvoice saved = repository.save(invoice);
        publish(saved, "InvoiceRegistered");
        return toView(saved);
    }

    @Transactional
    public BillingApi.InvoiceView issue(UUID id, BillingApi.IssueInvoiceCommand command) {
        SalesInvoice invoice = find(id);
        if (command != null && command.taxRate() != null) {
            invoice.assignPricing(invoice.getAmount(), command.taxRate());
        }
        try {
            if (command != null && command.pdfStorageKey() != null && !command.pdfStorageKey().isBlank()) {
                pdfService.validatedKey(command.pdfStorageKey());
                pdfService.resource(command.pdfStorageKey());
            }
            invoice.issue(command == null ? null : command.externalInvoiceNo(),
                    command == null ? null : command.issuedAt(), command == null ? null : command.invoiceType(),
                    command == null ? null : command.pdfFileName(), command == null ? null : command.pdfStorageKey(),
                    command == null ? null : command.extractedText());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ConflictException("INVOICE_ISSUE_INVALID", exception.getMessage());
        }
        SalesInvoice saved = repository.save(invoice);
        publish(saved, "InvoiceIssued");
        return toView(saved);
    }

    public BillingApi.PdfExtractionView extractPdf(MultipartFile file) {
        var parsed = pdfService.extract(file);
        var order = referencePort.findOrderBySerialNumbers(parsed.vinCandidates());
        if (order.isEmpty()) order = referencePort.findOrderByOrderNos(parsed.orderNoCandidates());
        var matched = order.orElse(null);
        return new BillingApi.PdfExtractionView(parsed.storageKey(), parsed.fileName(), parsed.rawText(),
                parsed.invoiceNo(), parsed.invoiceType(), parsed.type(), parsed.amount(), parsed.taxAmount(),
                parsed.taxRate(), parsed.issuedAt(), parsed.sellerName(), parsed.sellerTaxNo(), parsed.buyerName(),
                parsed.buyerTaxNo(), parsed.vinCandidates(), parsed.orderNoCandidates(),
                matched == null ? null : matched.orderId(), matched == null ? null : matched.orderNo(),
                matched == null ? null : matched.customerId(), matched == null ? null : matched.customerName(),
                parsed.confidence(), parsed.textExtracted(), parsed.warning());
    }

    @Transactional(readOnly = true)
    public Resource pdf(UUID id) {
        SalesInvoice invoice = find(id);
        if (invoice.getPdfStorageKey() == null) throw new NotFoundException("销项发票 PDF", id);
        return pdfService.resource(invoice.getPdfStorageKey());
    }

    @Transactional(readOnly = true)
    public String pdfFileName(UUID id) {
        return find(id).getPdfFileName();
    }

    @Transactional
    public BillingApi.InvoiceView voidInvoice(UUID id) {
        SalesInvoice invoice = find(id);
        try {
            invoice.voidInvoice();
        } catch (IllegalStateException exception) {
            throw new ConflictException("INVOICE_VOID_INVALID", exception.getMessage());
        }
        return toView(repository.save(invoice));
    }

    /** 红冲：原蓝票置为已红冲，并生成一张关联原票的红字发票。 */
    @Transactional
    public BillingApi.InvoiceView redFlush(UUID id) {
        SalesInvoice original = find(id);
        if (original.getType() == InvoiceType.RED) {
            throw new ConflictException("INVOICE_RED_FLUSH_INVALID", "红字发票不能再红冲");
        }
        try {
            original.redFlush(null);
        } catch (IllegalStateException exception) {
            throw new ConflictException("INVOICE_RED_FLUSH_INVALID", exception.getMessage());
        }
        repository.save(original);
        SalesInvoice red = SalesInvoice.create(
                "INV-R-%06d".formatted(repository.nextInvoiceSequence()), InvoiceDirection.OUT, InvoiceType.RED,
                original.getOrderId(), original.getDeliveryId(), original.getCustomerId(), original.getAmount(),
                original.getTaxRate(), "红冲原发票 " + original.getInvoiceNo());
        red.assignSerialNos(original.getSerialNos());
        red.markRedFlushOf(original.getId());
        SalesInvoice saved = repository.save(red);
        publish(original, "InvoiceRedFlushed");
        return toView(saved);
    }

    private SalesInvoice find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("发票", id));
    }

    private BillingApi.InvoiceView toView(SalesInvoice invoice) {
        String orderNo = invoice.getOrderId() == null ? null
                : referencePort.requireOrder(invoice.getOrderId()).orderNo();
        return BillingApi.InvoiceView.from(invoice, orderNo, referencePort.customerName(invoice.getCustomerId()));
    }

    private void publish(SalesInvoice invoice, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        putIfPresent(payload, "invoiceNo", invoice.getInvoiceNo());
        putIfPresent(payload, "type", invoice.getType() == null ? null : invoice.getType().name());
        putIfPresent(payload, "serialNos", invoice.getSerialNos());
        putIfPresent(payload, "amount", invoice.getAmount());
        putIfPresent(payload, "taxAmount", invoice.getTaxAmount());
        putIfPresent(payload, "status", invoice.getStatus() == null ? null : invoice.getStatus().name());
        putIfPresent(payload, "originalInvoiceId", invoice.getOriginalInvoiceId());
        putIfPresent(payload, "externalInvoiceNo", invoice.getExternalInvoiceNo());
        events.publish(new EventEnvelope(UUID.randomUUID(), eventType, "SalesInvoice", invoice.getId(),
                invoice.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
    }

    private List<String> normalizeSerialNos(List<String> serialNos) {
        if (serialNos == null) return List.of();
        return new LinkedHashSet<>(serialNos.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase())
                .toList()).stream().toList();
    }

    private void ensureSerialsNotInvoiced(UUID orderId, List<String> serialNos) {
        Set<String> requested = new LinkedHashSet<>(serialNos);
        Set<String> existing = new LinkedHashSet<>();
        for (SalesInvoice invoice : repository.findByOrderIdOrderByCreatedAtDesc(orderId)) {
            if (invoice.getType() != InvoiceType.BLUE || invoice.getStatus() == InvoiceStatus.VOID
                    || invoice.getSerialNos() == null || invoice.getSerialNos().isBlank()) continue;
            for (String value : invoice.getSerialNos().split(",")) {
                if (!value.isBlank()) existing.add(value.trim().toUpperCase());
            }
        }
        requested.retainAll(existing);
        if (!requested.isEmpty()) {
            throw new ConflictException("INVOICE_SERIAL_ALREADY_USED",
                    "以下 VIN/SN 已登记销项发票：" + String.join("、", requested));
        }
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) payload.put(key, value);
    }
}