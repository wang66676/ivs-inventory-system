package com.ivs.billing.invoice;

import com.ivs.billing.invoice.api.BillingApi;
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

/** 销项发票：开票、作废、红冲（红字发票必须关联原蓝票）。 */
@Service
public class SalesInvoiceApplicationService {

    private final SalesInvoiceRepository repository;
    private final BillingReferencePort referencePort;
    private final EventPublisher events;

    public SalesInvoiceApplicationService(SalesInvoiceRepository repository,
                                          BillingReferencePort referencePort,
                                          EventPublisher events) {
        this.repository = repository;
        this.referencePort = referencePort;
        this.events = events;
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
            if (amount == null) {
                amount = order.deliveredAmount();
            }
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

    @Transactional
    public BillingApi.InvoiceView issue(UUID id, BillingApi.IssueInvoiceCommand command) {
        SalesInvoice invoice = find(id);
        if (command != null && command.taxRate() != null) {
            invoice.assignPricing(invoice.getAmount(), command.taxRate());
        }
        try {
            invoice.issue(command == null ? null : command.externalInvoiceNo());
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new ConflictException("INVOICE_ISSUE_INVALID", exception.getMessage());
        }
        SalesInvoice saved = repository.save(invoice);
        publish(saved, "InvoiceIssued");
        return toView(saved);
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
        // 事件信封使用 Map.copyOf，null 值会直接抛异常，因此这里只放非空字段
        putIfPresent(payload, "invoiceNo", invoice.getInvoiceNo());
        putIfPresent(payload, "type", invoice.getType() == null ? null : invoice.getType().name());
        putIfPresent(payload, "amount", invoice.getAmount());
        putIfPresent(payload, "taxAmount", invoice.getTaxAmount());
        putIfPresent(payload, "status", invoice.getStatus() == null ? null : invoice.getStatus().name());
        putIfPresent(payload, "originalInvoiceId", invoice.getOriginalInvoiceId());
        putIfPresent(payload, "externalInvoiceNo", invoice.getExternalInvoiceNo());
        events.publish(new EventEnvelope(UUID.randomUUID(), eventType, "SalesInvoice", invoice.getId(),
                invoice.getVersion(), null, UUID.randomUUID(), Instant.now(), payload));
    }

    private void putIfPresent(Map<String, Object> payload, String key, Object value) {
        if (value != null) {
            payload.put(key, value);
        }
    }
}